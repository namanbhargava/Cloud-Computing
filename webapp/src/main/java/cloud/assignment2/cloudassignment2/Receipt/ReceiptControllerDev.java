package cloud.assignment2.cloudassignment2.Receipt;

import cloud.assignment2.cloudassignment2.Expense.ExpensePojo;
import cloud.assignment2.cloudassignment2.Expense.ExpenseRepository;
import cloud.assignment2.cloudassignment2.user.UserDao;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.gson.JsonObject;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

@RestController
@Configuration
@Profile("dev")
public class ReceiptControllerDev {

    @Autowired
    UserDao userDao;

    @Autowired
    ExpenseRepository expenseRepository;

    @Autowired
    ReceiptRepository receiptRepository;

    @Autowired
    Environment env;


    //@Value("${app.profile.name}")
      //      private String profileName;

    String clientRegion = "us-east-1";
    String bucketName = "csye6225-fall2018-bhargavan.me";

    @RequestMapping(value="/transaction/{id}/attachments" , method = RequestMethod.POST)
    public String uploadReceipt(@PathVariable(value="id") String transactionId, @RequestParam("file") MultipartFile file, HttpServletRequest req,
                                HttpServletResponse res){

        System.out.println("DEV Environment");
        JsonObject json = new JsonObject();

        String fileName = file.getOriginalFilename();
        String header = req.getHeader("Authorization");
        if(header != null) {
            int result = userDao.authUserCheck(header);
            if(result>0)
            {
                List<ExpensePojo> expensePojoRecord = expenseRepository.findAllById(transactionId);
                if(expensePojoRecord.size()>0){
                    ExpensePojo expenseRecord = expensePojoRecord.get(0);
                    if(Integer.parseInt(expenseRecord.getUserId()) == result)
                    {
                        // Upload to Amazon S3 Start
                        try {
                            System.out.println("app.profile.name - "+ env.getProperty("app.profile.name"));
                            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                                    .withRegion(clientRegion)
                                    .withCredentials(new InstanceProfileCredentialsProvider(false))
                                    .build();
                            String uploadDir = "/uploads/";
                            String realPath2Upload = req.getServletContext().getRealPath(uploadDir);
                            if(! new File(realPath2Upload).exists())
                            {
                                new File(realPath2Upload).mkdir();
                            }

                            String filePath2Upload = realPath2Upload+transactionId+file.getOriginalFilename();
                            String keyName = transactionId+file.getOriginalFilename();
                            File saveFile = new File(filePath2Upload);
                            file.transferTo(saveFile);

                            ObjectMetadata metadata = new ObjectMetadata();
                            metadata.setContentLength(file.getSize());
                            InputStream inputStream = new FileInputStream(saveFile);
                            s3Client.putObject(new PutObjectRequest(bucketName, keyName, inputStream, metadata).withCannedAcl(CannedAccessControlList.PublicRead));

                            ReceiptPojo receiptPojo = new ReceiptPojo();
                            receiptPojo.setTransactionId(transactionId);
                            receiptPojo.setUrl(keyName);
                            receiptPojo.setUserId(String.valueOf(result));
                            receiptRepository.save(receiptPojo);
                            res.setStatus(HttpServletResponse.SC_OK);
                            json.addProperty("message","File uploaded");

                        }
                        catch(AmazonServiceException e) {
                            // The call was transmitted successfully, but Amazon S3 couldn't process
                            // it, so it returned an error response.
                            e.printStackTrace();
                        }
                        catch(SdkClientException e) {
                            // Amazon S3 couldn't be contacted for a response, or the client
                            // couldn't parse the response from Amazon S3.
                            e.printStackTrace();
                        //} catch (IOException e) {
                           // e.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        // Upload to Amazon S3 End





                    }
                    else{
                        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        json.addProperty("message","You are unauthorized. UserId do not match");
                    }

                }
                else{
                    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    json.addProperty("message","Bad Request! No id found");
                }
            }
            else{
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                json.addProperty("message","You are unauthorized");
            }

        }else{
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            json.addProperty("message","You are unauthorized");
        }
        return json.toString();

    }
    //DELETE RECEIPT START
    //key = filename to delete
    @RequestMapping(value="/transaction/{id}/attachments/{idAttachment}" , method = RequestMethod.DELETE)
    public String deleteReceipt(@PathVariable(value="id") String transactionId,
                              @PathVariable(value="idAttachment") String attachmentId,
                              HttpServletRequest req, HttpServletResponse res){

        String keyName;
        //get file name wrt receiptId from receipt_pojo
        JsonObject json = new JsonObject();

        String header = req.getHeader("Authorization");
        if(header != null) {
            int result = userDao.authUserCheck(header);
            if(result>0){
                if(transactionId!="") {
                    if (attachmentId != ""){
                        List<ReceiptPojo> rpList = receiptRepository.findByReceiptid(attachmentId);
                        ReceiptPojo rp = rpList.get(0);
                        System.out.println("Receipt has tx id as" + rp.getTransactionId());
                        keyName = rp.getUrl();
                        if(rp.getTransactionId().equals(transactionId)){
                            if(Integer.parseInt(rp.getUserId()) == result)
                            {
                                AmazonS3 s3client = AmazonS3ClientBuilder.standard()
                                        .withRegion(clientRegion)
                                        .withCredentials(new InstanceProfileCredentialsProvider(false))
                                        .build();
                                s3client.deleteObject(bucketName, keyName);

                                receiptRepository.delete(rp);
                                res.setStatus(HttpServletResponse.SC_NO_CONTENT);
                                json.addProperty("message","Record deleted Successfully");
                                return json.toString();
                            }
                            else{
                                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                return json.toString();
                            }
                        }

                    }
                    else{
                        json.addProperty("message", "Invalid attachment Id.");
                        return json.toString();
                    }
                }
                else{
                    json.addProperty("message", "Invalid Expense Id.");
                    return json.toString();
                }
            }
            else{
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                json.addProperty("message","You are unauthorized");
            }

        }
        else{
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            json.addProperty("message","You are unauthorized");
        }

        return null;
    }
    //DELETE RECEIPT END



    //GET RECEIPT START
    @RequestMapping(value="/transaction/{id}/attachments", method=RequestMethod.GET)
    public List<ReceiptPojo> getReceipt(@PathVariable(value="id") String transactionId, HttpServletRequest req, HttpServletResponse res){

        JsonObject json = new JsonObject();
        System.out.println("DEV Environment");
        String authHeader = req.getHeader("Authorization");

        if (authHeader==null){
            List<ReceiptPojo> newpojo1 = new ArrayList<ReceiptPojo>();
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return newpojo1;
        }

        else {
            int result = userDao.authUserCheck(authHeader);
            if(result>0){
                List<ExpensePojo> expensePojoRecord = expenseRepository.findAllById(transactionId);

                if(expensePojoRecord.size()>0){
                    ExpensePojo expenseRecord = expensePojoRecord.get(0);
                    if(Integer.parseInt(expenseRecord.getUserId()) == result){
                        res.setStatus(HttpServletResponse.SC_OK);
                        return receiptRepository.findByTransactionId(transactionId);
                    }
                    else{
                        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        List<ReceiptPojo> newpojo1 = new ArrayList<ReceiptPojo>();
                        return newpojo1;
                    }
                }
                else{
                    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    List<ReceiptPojo> newpojo1 = new ArrayList<ReceiptPojo>();
                    return newpojo1;
                }
            }
            else{
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                List<ReceiptPojo> newpojo1 = new ArrayList<ReceiptPojo>();
                return newpojo1;
            }

        }
    }
    //GET RECEIPT END

    // UPDATE RECEIPT START
    @RequestMapping(value="/transaction/{id}/attachments/{idAttachments}", method=RequestMethod.PUT)
    public String updateReceipt(@PathVariable(value="id") String transactionId,
                                @PathVariable(value="idAttachments") String attachmentId,
                                @RequestParam ("file") MultipartFile file,
                                HttpServletRequest req, HttpServletResponse res){

        System.out.println(" DEV Environment");
        JsonObject json = new JsonObject();
        String keyName = transactionId+file.getOriginalFilename();

        String header = req.getHeader("Authorization");
        if(header != null) {
            int result = userDao.authUserCheck(header);
            if(result>0){
                if(transactionId!="") {
                    if (attachmentId != ""){
                        List<ReceiptPojo> rpList = receiptRepository.findByReceiptid(attachmentId);
                        ReceiptPojo rp = rpList.get(0);
                        System.out.println("Receipt has tx id as" + rp.getTransactionId());
                        if(rp.getTransactionId().equals(transactionId)){
                            if(Integer.parseInt(rp.getUserId()) == result){


                                // Upload to Amazon S3 Start
                                try {
                                    AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                                            .withRegion(clientRegion)
                                            .withCredentials(new InstanceProfileCredentialsProvider(false))
                                            .build();

                                    // Delete the file from S3
                                    s3Client.deleteObject(bucketName, rp.getUrl());

                                    //receiptRepository.delete(rp);
                                    //res.setStatus(HttpServletResponse.SC_NO_CONTENT);
                                    //json.addProperty("message","Record deleted Successfully");
                                    // Delete the file from S3

                                    String uploadDir = "/uploads/";
                                    String realPath2Upload = req.getServletContext().getRealPath(uploadDir);
                                    if(! new File(realPath2Upload).exists())
                                    {
                                        new File(realPath2Upload).mkdir();
                                    }

                                    String filePath2Upload = realPath2Upload+transactionId+file.getOriginalFilename();
                                    //String keyName = file.getOriginalFilename()+transactionId;
                                    File saveFile = new File(filePath2Upload);
                                    file.transferTo(saveFile);

                                    ObjectMetadata metadata = new ObjectMetadata();
                                    metadata.setContentLength(file.getSize());
                                    InputStream inputStream = new FileInputStream(saveFile);
                                    s3Client.putObject(new PutObjectRequest(bucketName, keyName, inputStream, metadata).withCannedAcl(CannedAccessControlList.PublicRead));

                                    rp.setTransactionId(rp.getTransactionId());
                                    rp.setUrl(keyName);
                                    rp.setUserId(rp.getUserId());
                                    receiptRepository.save(rp);
                                    res.setStatus(HttpServletResponse.SC_OK);
                                    json.addProperty("message","Record updated!");
                                    return json.toString();

                                }
                                catch(AmazonServiceException e) {
                                    // The call was transmitted successfully, but Amazon S3 couldn't process
                                    // it, so it returned an error response.
                                    e.printStackTrace();
                                }
                                catch(SdkClientException e) {
                                    // Amazon S3 couldn't be contacted for a response, or the client
                                    // couldn't parse the response from Amazon S3.
                                    e.printStackTrace();
                                } catch (Exception e) {
                                 e.printStackTrace();
                                }
                                // Upload to Amazon S3 End



                            }
                            else{
                                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                return json.toString();
                            }
                        }
                    }
                    else{
                        json.addProperty("message", "Invalid attachment Id.");
                        return json.toString();
                    }
                }
                else{
                    json.addProperty("message", "Invalid Expense Id.");
                    return json.toString();
                }
            }
            else{
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                json.addProperty("message","You are unauthorized");
            }
        }
        else{
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            json.addProperty("message","You are unauthorized");
        }

        return null;

    }
    // UPDATE RECEIPT END

    public File convertFromMultipart(MultipartFile file) throws Exception {
        File newFile = new File(file.getOriginalFilename());
        newFile.mkdir();
        //newFile.mkdir();
        newFile.setReadable(true, false);
        newFile.setWritable(true, false);
        newFile.createNewFile();
        FileOutputStream fs = new FileOutputStream(newFile);
        fs.write(file.getBytes());
        fs.close();
        return newFile;
    }

}

