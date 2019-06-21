#include <iostream>
#include <string>

#include <grpcpp/grpcpp.h>

#include "botan_all.h"
#include "autheid_utils.h"
#include "rp.grpc.pb.h"

using namespace autheid;

const char *apiKey = "Pj+GIg2/l7ZKmicZi37+1giqKJ1WH3Vt8vSSxCuvPkKD";

void initCtx(grpc::ClientContext *ctx) {
   ctx->AddMetadata("authorization", std::string("Bearer ") + apiKey);
}

bool makeRequest(PrivateKey *raPrivKey) {
   auto channel = grpc::CreateChannel("api.staging.autheid.com",
      grpc::SslCredentials(grpc::SslCredentialsOptions()));
   auto stub = autheid::rp::Requests::NewStub(channel);

   rp::CreateRequest createRequest;
   createRequest.set_type(rp::DEVICE_KEY);
   createRequest.set_title("Test");
   createRequest.set_email("test@example.com");
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

   std::string deviceKeyHex = Botan::hex_encode(
      reinterpret_cast<const uint8_t*>(deviceKey.data()), deviceKey.size());
   std::cout << "got device key: " << deviceKeyHex << std::endl;

   return true;
}

int main(int argc, char** argv) {
   // Make request without device key encryption
   if (!makeRequest(nullptr)) {
      return 1;
   }

   auto raPrivKey = autheid::generatePrivateKey();

   // Make request with device key encryption
   if (!makeRequest(&raPrivKey)) {
      return 1;
   }

   return 0;
}
