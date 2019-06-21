#include <iostream>
#include <string>

#include <grpcpp/grpcpp.h>

#include "botan_all.h"
#include "autheid_utils.h"
#include "rp.grpc.pb.h"

using namespace autheid;

namespace {

const char ApiKey[] = "Pj+GIg2/l7ZKmicZi37+1giqKJ1WH3Vt8vSSxCuvPkKD";

const char TestEmail[] = "test@example.com";
const char TestTitle[] = "Test";

const char RootCA[] = "-----BEGIN CERTIFICATE-----\n"
   "MIICpzCCAi2gAwIBAgIVANCWAAe4NB0AbzvcOMzVt/eXvJOEMAoGCCqGSM49BAMD\n"
   "MHQxCzAJBgNVBAYTAlNFMSEwHwYDVQQKExhUZXN0IEF1dGhlbnRpY2F0ZSBlSUQg\n"
   "QUIxHzAdBgNVBAsTFlRlc3QgSW5mcmFzdHJ1Y3R1cmUgQ0ExITAfBgNVBAMTGFRl\n"
   "c3QgQXV0aCBlSUQgUm9vdCBDQSB2MTAiGA8yMDE5MDUyMTAwMDAwMFoYDzIwMzkw\n"
   "NTIxMDAwMDAwWjB0MQswCQYDVQQGEwJTRTEhMB8GA1UEChMYVGVzdCBBdXRoZW50\n"
   "aWNhdGUgZUlEIEFCMR8wHQYDVQQLExZUZXN0IEluZnJhc3RydWN0dXJlIENBMSEw\n"
   "HwYDVQQDExhUZXN0IEF1dGggZUlEIFJvb3QgQ0EgdjEwdjAQBgcqhkjOPQIBBgUr\n"
   "gQQAIgNiAATttsFmSGlfGgeBWCO+G4j+LaheRZksckdz0ks2DrUz+eBLAdY5neE1\n"
   "uwvidGXuebR4c3Kr7TbBZaQbmIHEd3kUTQ4paqKWQKgck5WJNYPm2wgpS7co8Fjk\n"
   "jaFG4Mu9QZujezB5MB0GA1UdDgQWBBRaWiMIx4yz8dBVsBVyR0qL9wVSejAOBgNV\n"
   "HQ8BAf8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB/zAWBgNVHSAEDzANMAsGCSqFcL2E\n"
   "PwEBATAfBgNVHSMEGDAWgBRaWiMIx4yz8dBVsBVyR0qL9wVSejAKBggqhkjOPQQD\n"
   "AwNoADBlAjBaJB4PI9hFk0teclJEPWfXUt1CovrWY3nWlOkyl+usJFkgJZH1yIFI\n"
   "uYVsbv9LK7QCMQCc9MQEu9tZLzVCcucBy2tbNYF1BPUE4Z51gohpiBTxbosqy9L2\n"
   "61lZsHVbH/v/HtY=\n"
   "-----END CERTIFICATE-----\n";

}

std::vector<uint8_t> convert(const std::string &data)
{
   return std::vector<uint8_t>(data.begin(), data.end());
}

void initCtx(grpc::ClientContext *ctx) {
   ctx->AddMetadata("authorization", std::string("Bearer ") + ApiKey);
}

bool makeRequest(PrivateKey *raPrivKey) {
   auto channel = grpc::CreateChannel("api.staging.autheid.com",
      grpc::SslCredentials(grpc::SslCredentialsOptions()));
   auto stub = autheid::rp::Requests::NewStub(channel);

   rp::CreateRequest createRequest;
   createRequest.set_type(rp::DEVICE_KEY);
   createRequest.set_title(TestTitle);
   createRequest.set_email(TestEmail);
   createRequest.mutable_device_key()->set_key_id("Test");
   createRequest.mutable_device_key()->set_use_new_devices(true);
   if (raPrivKey) {
      auto raPubKey = getPublicKey(*raPrivKey);
      createRequest.set_ra_pub_key(raPubKey.data(), raPubKey.size());
   }

   rp::CreateResponse createResponse;
   grpc::ClientContext createCtx;
   initCtx(&createCtx);
   auto status = stub->Create(&createCtx, createRequest, &createResponse);
   if (!status.ok()) {
      std::cerr << "request failed: " << status.error_message() << std::endl;
      return false;
   }

   std::string requestId = createResponse.request_id();
   std::cout << "request created: " << requestId << std::endl;

   rp::GetResultRequest resultRequest;
   resultRequest.set_request_id(requestId);

   grpc::ClientContext resultCtx;
   initCtx(&resultCtx);
   rp::GetResultResponse resultResponse;

   status = stub->GetResult(&resultCtx, resultRequest, &resultResponse);
   if (!status.ok()) {
      std::cerr << "get result failed: " << status.error_message() << std::endl;
      return false;
   }

   std::string deviceKey;

   if (raPrivKey) {
      auto payload = decryptData(resultResponse.device_key_enc().data(),
         resultResponse.device_key_enc().size(), *raPrivKey);
      if (payload.empty()) {
         std::cerr << "decryptData failed" << std::endl;
         return false;
      }

      rp::GetResultResponse::DeviceKeyResult deviceKeyResult;
      if (!deviceKeyResult.ParseFromArray(payload.data(), int(payload.size()))) {
         std::cerr << "parse payload failed" << std::endl;
         return false;
      }

      deviceKey = deviceKeyResult.device_key();
   } else {
      deviceKey = resultResponse.device_key().device_key();
   }

   std::string deviceKeyHex = Botan::hex_encode(convert(deviceKey));;
   std::cout << "got device key: " << deviceKeyHex << std::endl;

   return true;
}

bool verifySignature(const rp::GetResultResponse::SignatureResult &signature)
{
   try {
      // DER encoding
      Botan::DataSource_Memory clientRaw(signature.certificate_client());
      Botan::X509_Certificate client(clientRaw);

      // DER encoding
      Botan::DataSource_Memory issuerRaw(signature.certificate_issuer());
      Botan::X509_Certificate issuer(issuerRaw);

      Botan::DataSource_Memory rootRaw(RootCA);
      Botan::X509_Certificate root(rootRaw);

      auto clientPubKey = client.load_subject_public_key();

      Botan::PK_Verifier verifier(*clientPubKey, "EMSA1(SHA-256)", Botan::DER_SEQUENCE);
      verifier.update(convert(signature.signature_data()));
      bool result = verifier.check_signature(convert(signature.sign()));
      if (!result) {
         std::cerr << "invalid sign detected" << std::endl;
         return false;
      }

      rp::GetResultResponse::SignatureResult::SignatureData signData;
      result = signData.ParseFromString(signature.signature_data());
      if (!result) {
         std::cerr << "signature parse failed" << std::endl;
         return false;
      }

      if (signData.title() != TestTitle || signData.email() != TestEmail) {
         std::cerr << "invalid signature data" << std::endl;
         return false;
      }

      result = client.check_signature(*issuer.load_subject_public_key());
      if (!result) {
         std::cerr << "invalid client's certificate" << std::endl;
         return false;
      }

      result = issuer.check_signature(*root.load_subject_public_key());
      if (!result) {
         std::cerr << "invalid issuer's certificate" << std::endl;
         return false;
      }

      Botan::OCSP::Response ocsp(convert(signature.ocsp_response()));
      Botan::Certificate_Status_Code verifyResult = ocsp.status_for(issuer, client);
      if (verifyResult != Botan::Certificate_Status_Code::OCSP_RESPONSE_GOOD) {
         std::cerr << "invalid ocsp response: " << int(verifyResult) << std::endl;
         return false;
      }

      return true;
   } catch (const std::exception &e) {
      std::cerr << "signature verify failed: " << e.what() << std::endl;
      return false;
   }
}

bool makeSignatureRequest() {
   auto channel = grpc::CreateChannel("api.staging.autheid.com",
      grpc::SslCredentials(grpc::SslCredentialsOptions()));
   auto stub = autheid::rp::Requests::NewStub(channel);

   rp::CreateRequest createRequest;
   createRequest.set_type(rp::SIGNATURE);
   createRequest.set_title(TestTitle);
   createRequest.set_email(TestEmail);
   createRequest.mutable_signature()->set_serialization(rp::SERIALIZATION_PROTOBUF);

   rp::CreateResponse createResponse;
   grpc::ClientContext createCtx;
   initCtx(&createCtx);
   auto status = stub->Create(&createCtx, createRequest, &createResponse);
   if (!status.ok()) {
      std::cerr << "request failed: " << status.error_message() << std::endl;
      return false;
   }

   std::string requestId = createResponse.request_id();
   std::cout << "request created: " << requestId << std::endl;

   rp::GetResultRequest resultRequest;
   resultRequest.set_request_id(requestId);

   grpc::ClientContext resultCtx;
   initCtx(&resultCtx);
   rp::GetResultResponse resultResponse;

   status = stub->GetResult(&resultCtx, resultRequest, &resultResponse);
   if (!status.ok()) {
      std::cerr << "get result failed: " << status.error_message() << std::endl;
      return false;
   }

   if (!resultResponse.has_signature()) {
      std::cerr << "signature is missing" << std::endl;
      return false;
   }

   if (!verifySignature(resultResponse.signature())) {
      std::cerr << "signature verification failed" << std::endl;
      return false;
   }

   std::cout << "signature succeed" << std::endl;
   return true;
}

int main(int argc, char** argv) {
   // Make request without device key encryption
   if (!makeRequest(nullptr)) {
      return EXIT_FAILURE;
   }

   auto raPrivKey = autheid::generatePrivateKey();

   // Make request with device key encryption
   if (!makeRequest(&raPrivKey)) {
      return EXIT_FAILURE;
   }

   if (makeSignatureRequest()) {
      return EXIT_FAILURE;
   }

   return EXIT_SUCCESS;
}
