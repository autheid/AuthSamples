package com.autheid.examples.simple;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.autheid.api.Rp;
import com.autheid.api.RequestsGrpc;

import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;


public class SimpleClient {
  private static final Logger logger = Logger.getLogger(SimpleClient.class.getName());

  private final ManagedChannel channel;
  private final RequestsGrpc.RequestsBlockingStub blockingStub;

  private final static String apiKey = "Pj+Q9SsZloftMkmE7EhA8v2Bz1ZC9aOmUkAKTBW9hagJ";
  private final static String server = "api.staging.autheid.com";

  private final static String rootCA = "-----BEGIN CERTIFICATE-----\n" +
      "MIIBYTCCAQigAwIBAgIUTS0DdUWXx6vJmOx/F3g+jehUoyUwCgYIKoZIzj0EAwIw\n" +
      "DzENMAsGA1UEAxMEUm9vdDAeFw0xOTA0MjExMjQyMDBaFw0yNDA0MTkxMjQyMDBa\n" +
      "MA8xDTALBgNVBAMTBFJvb3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASWm3OT\n" +
      "csvuSZLY/WzPtbEx996x+LUlCd/GdSe+A/aw86yEbK5gm9p/Vxq7LwPOIOHO/Q7X\n" +
      "Vtz21asiT/mwmK2Yo0IwQDAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB\n" +
      "/zAdBgNVHQ4EFgQUWkoQA0l2AX7fe5fs7VbMuDc/T5UwCgYIKoZIzj0EAwIDRwAw\n" +
      "RAIgVEPhdjpwyGgoulHW/QgZREEd59l279o6g3cCE6acdA0CIByuVzaaUHBioXGK\n" +
      "DM3jUmdE6/OQTERUgZSxp4gtLIDu\n" +
      "-----END CERTIFICATE-----\n";

  private SimpleClient(String host, int port) {
    this(ManagedChannelBuilder.forAddress(host, port).useTransportSecurity().build());
  }

  private SimpleClient(ManagedChannel channel) {
    this.channel = channel;

    Metadata header = new Metadata();
    Metadata.Key<String> key = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    header.put(key, "Bearer " + apiKey);

    RequestsGrpc.RequestsBlockingStub stub = RequestsGrpc.newBlockingStub(channel);
    blockingStub = MetadataUtils.attachHeaders(stub, header);
  }

  private void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  private void makeRequestKYC(String email)
  {
    logger.info("Send KYC request to " + email + " ...");
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

  private void makeRequestSignature(String email) throws Exception
  {
    logger.info("Send Signature request to " + email + " ...");
    try {
      String title = "Auth eID Test";

      Rp.CreateRequest request = Rp.CreateRequest.newBuilder()
          .setTitle(title).setEmail(email)
          .setType(Rp.RequestType.SIGNATURE)
          .build();

      Rp.CreateResponse response = blockingStub.create(request);

      String requestId = response.getRequestId();
      logger.info("Request ID: " + requestId);

      Rp.GetResultRequest resultRequest = Rp.GetResultRequest.newBuilder()
          .setRequestId(requestId)
          .build();

      Rp.GetResultResponse resultResponse = blockingStub.getResult(resultRequest);

      if (resultResponse.getStatus() == Rp.RequestStatus.SUCCESS) {
        byte[] data = resultResponse.getSignature().getSignatureData().toByteArray();
        byte[] sign = resultResponse.getSignature().getSign().toByteArray();
        byte[] ocsp = resultResponse.getSignature().getOcspResponse().toByteArray();
        X509Certificate root = loadCert(rootCA.getBytes());
        X509Certificate client = loadCert(resultResponse.getSignature().getCertificateClient().toByteArray());
        X509Certificate issuer = loadCert(resultResponse.getSignature().getCertificateIssuer().toByteArray());

        client.verify(issuer.getPublicKey());
        issuer.verify(root.getPublicKey());

        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(client.getPublicKey());
        signature.update(data);

        if (!signature.verify(sign)) {
          throw new Exception("invalid signature");
        }

        OCSPResp resp = new OCSPResp(ocsp);
        BasicOCSPResp basicResp = (BasicOCSPResp)resp.getResponseObject();

        ContentVerifierProvider verifier = new JcaContentVerifierProviderBuilder()
            .setProvider(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).build(issuer);
        if (!basicResp.isSignatureValid(verifier)) {
          throw new Exception("Invalid OCSP signature!");
        }

        SingleResp singleResp = basicResp.getResponses()[0];

        CertificateStatus status = singleResp.getCertStatus();
        if (status != CertificateStatus.GOOD) {
          throw new Exception("Invalid client's certificate status");
        }

        if (!singleResp.getCertID().getSerialNumber().equals(client.getSerialNumber())) {
          throw new Exception("Invalid OCSP response!");
        }

        Date signTimestamp = new Date(resultResponse.getTimestampCreated() * 1000L);
        if (signTimestamp.compareTo(singleResp.getThisUpdate()) < 0) {
          throw new Exception("Invalid OCSP response!");
        }
        if (signTimestamp.compareTo(singleResp.getNextUpdate()) > 0) {
          throw new Exception("Invalid OCSP response!");
        }
      }

      logger.info("Result: " + resultResponse.getStatus());
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
    }
  }

  private static X509Certificate loadCert(byte[] data) throws Exception
  {
    CertificateFactory fact = CertificateFactory.getInstance("X.509");
    ByteArrayInputStream is = new ByteArrayInputStream(data);
    X509Certificate cert = (X509Certificate) fact.generateCertificate(is);
    return cert;
  }

  public static void main(String[] args) throws Exception {
    // Needed for OCSP validation
    Security.addProvider(new BouncyCastleProvider());

    SimpleClient client = new SimpleClient(server, 443);
    try {
      String email = "test3@verified-fast.com";
      if (args.length > 0) {
        email = args[0];
      }
      client.makeRequestKYC(email);
      client.makeRequestSignature(email);
    } finally {
      client.shutdown();
    }
  }
}
