from __future__ import print_function

import grpc

import rp_pb2
import rp_pb2_grpc

apiKey = "Pj+Q9SsZloftMkmE7EhA8v2Bz1ZC9aOmUkAKTBW9hagJ"
email = "test3@verified-fast.com"

metadata = [("authorization", "Bearer " + apiKey)]

def send_request(stub):
    create_request = rp_pb2.CreateRequest()
    create_request.title = "Test"
    create_request.email = email
    create_request.type = rp_pb2.KYC
    create_request.kyc.files_format = rp_pb2.EMBEDDED
    create_response = stub.Create(create_request, metadata=metadata)
    request_id = create_response.request_id
    print("Request ID: ", request_id)

    get_result_request = rp_pb2.GetResultRequest()
    get_result_request.request_id = request_id
    get_result_request = stub.GetResult(get_result_request, metadata=metadata)
    print("Request result: ", rp_pb2.RequestStatus.Name(get_result_request.status))
    if get_result_request.status == rp_pb2.SUCCESS:
        print("Fist Name: ", get_result_request.kyc.identification.first_name)
        print("Last Name: ", get_result_request.kyc.identification.last_name)
        print("File size: ", len(get_result_request.kyc.kyc_pdf.embedded))
        try:
            f = open("autheid_kyc.pdf", "wb")
            f.write(get_result_request.kyc.kyc_pdf.embedded)
            f.close()
        except IOError:
            print("write file failed")

def main():
    with grpc.secure_channel("api.staging.autheid.com", grpc.ssl_channel_credentials()) as channel:
        stub = rp_pb2_grpc.RequestsStub(channel)

        send_request(stub)

if __name__ == "__main__":
    main()
