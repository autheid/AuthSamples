package com.autheid.examples.simple;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.autheid.api.Rp;
import com.autheid.api.RequestsGrpc;


public class SimpleClient {
  private static final Logger logger = Logger.getLogger(SimpleClient.class.getName());

  private final ManagedChannel channel;
  private final RequestsGrpc.RequestsBlockingStub blockingStub;

  private final static String apiKey = "Pj+Q9SsZloftMkmE7EhA8v2Bz1ZC9aOmUkAKTBW9hagJ";
  private final static String server = "api.staging.autheid.com";

  public SimpleClient(String host, int port) {
    this(ManagedChannelBuilder.forAddress(host, port).useTransportSecurity().build());
  }

  SimpleClient(ManagedChannel channel) {
    this.channel = channel;

    Metadata header = new Metadata();
    Metadata.Key<String> key = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    header.put(key, "Bearer " + apiKey);

    RequestsGrpc.RequestsBlockingStub stub = RequestsGrpc.newBlockingStub(channel);
    blockingStub = MetadataUtils.attachHeaders(stub, header);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  public void makeRequest(String email)
  {
    logger.info("Send request to " + email + " ...");
    try {
      String title = "Auth eID Test";
      Rp.CreateRequest.KYC kyc = Rp.CreateRequest.KYC.newBuilder()
          .setFilesFormat(Rp.FileFormat.EMBEDDED).build();

      Rp.CreateRequest request = Rp.CreateRequest.newBuilder()
          .setTitle(title).setEmail(email)
          .setType(Rp.RequestType.KYC)
          .setKyc(kyc)
          .build();

      Rp.CreateResponse response = blockingStub.create(request);

      String requestId = response.getRequestId();
      logger.info("Request ID: " + requestId);

      Rp.GetResultRequest resultRequest = Rp.GetResultRequest.newBuilder()
          .setRequestId(requestId)
          .build();

      Rp.GetResultResponse resultResponse = blockingStub.getResult(resultRequest);

      if (resultResponse.getStatus() == Rp.RequestStatus.SUCCESS) {
        logger.info("Fist Name: " + resultResponse.getKyc().getIdentification().getFirstName());
        logger.info("Last Name: " + resultResponse.getKyc().getIdentification().getLastName());

        int size = resultResponse.getKyc().getKycPdf().getEmbedded().size();
        logger.info("File size: " + size);

        FileOutputStream out = new FileOutputStream("autheid_kyc.pdf");
        out.write(resultResponse.getKyc().getKycPdf().getEmbedded().toByteArray());
        out.close();
      }

      logger.info("Result: " + resultResponse.getStatus());
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
    } catch (IOException e) {
      logger.log(Level.WARNING, "Can't save PDF file", e);
    }
  }


  public static void main(String[] args) throws Exception {
    SimpleClient client = new SimpleClient(server, 443);
    try {
      String email = "test3@verified-fast.com";
      if (args.length > 0) {
        email = args[0];
      }
      client.makeRequest(email);
    } finally {
      client.shutdown();
    }
  }
}
