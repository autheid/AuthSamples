from __future__ import print_function

import datetime
import grpc

import rp_pb2
import rp_pb2_grpc

from cryptography import x509
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes

apiKey = "Pj+Q9SsZloftMkmE7EhA8v2Bz1ZC9aOmUkAKTBW9hagJ"
email = "test@example.com"

metadata = [("authorization", "Bearer " + apiKey)]

root_cert = """-----BEGIN CERTIFICATE-----
MIICpzCCAi2gAwIBAgIVANCWAAe4NB0AbzvcOMzVt/eXvJOEMAoGCCqGSM49BAMD
MHQxCzAJBgNVBAYTAlNFMSEwHwYDVQQKExhUZXN0IEF1dGhlbnRpY2F0ZSBlSUQg
QUIxHzAdBgNVBAsTFlRlc3QgSW5mcmFzdHJ1Y3R1cmUgQ0ExITAfBgNVBAMTGFRl
c3QgQXV0aCBlSUQgUm9vdCBDQSB2MTAiGA8yMDE5MDUyMTAwMDAwMFoYDzIwMzkw
NTIxMDAwMDAwWjB0MQswCQYDVQQGEwJTRTEhMB8GA1UEChMYVGVzdCBBdXRoZW50
aWNhdGUgZUlEIEFCMR8wHQYDVQQLExZUZXN0IEluZnJhc3RydWN0dXJlIENBMSEw
HwYDVQQDExhUZXN0IEF1dGggZUlEIFJvb3QgQ0EgdjEwdjAQBgcqhkjOPQIBBgUr
gQQAIgNiAATttsFmSGlfGgeBWCO+G4j+LaheRZksckdz0ks2DrUz+eBLAdY5neE1
uwvidGXuebR4c3Kr7TbBZaQbmIHEd3kUTQ4paqKWQKgck5WJNYPm2wgpS7co8Fjk
jaFG4Mu9QZujezB5MB0GA1UdDgQWBBRaWiMIx4yz8dBVsBVyR0qL9wVSejAOBgNV
HQ8BAf8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB/zAWBgNVHSAEDzANMAsGCSqFcL2E
PwEBATAfBgNVHSMEGDAWgBRaWiMIx4yz8dBVsBVyR0qL9wVSejAKBggqhkjOPQQD
AwNoADBlAjBaJB4PI9hFk0teclJEPWfXUt1CovrWY3nWlOkyl+usJFkgJZH1yIFI
uYVsbv9LK7QCMQCc9MQEu9tZLzVCcucBy2tbNYF1BPUE4Z51gohpiBTxbosqy9L2
61lZsHVbH/v/HtY=
-----END CERTIFICATE-----
"""

def verify_ocsp(ocsp, cert, issuer):
    if ocsp.response_status != x509.ocsp.OCSPResponseStatus.SUCCESSFUL:
        raise Exception("invalid OCSP status")

    # verify OCSP signature
    issuer.public_key().verify(ocsp.signature, ocsp.tbs_response_bytes, ec.ECDSA(hashes.SHA384()))

    if ocsp.serial_number != cert.serial_number:
        raise Exception("invalid serial number in the OCSP response")

    if ocsp.certificate_status != x509.ocsp.OCSPCertStatus.GOOD:
        raise Exception("invalid certificate status in the OCSP response")

    if ocsp.next_update < datetime.datetime.now() - datetime.timedelta(seconds=30):
        raise Exception("invalid OCSP response next update time")

def send_request(stub):
    create_request = rp_pb2.CreateRequest()
    create_request.title = "Test"
    create_request.email = email
    create_request.type = rp_pb2.SIGNATURE
    create_request.signature.serialization = rp_pb2.SERIALIZATION_PROTOBUF
    create_response = stub.Create(create_request, metadata=metadata)
    request_id = create_response.request_id
    print("Request ID: ", request_id)

    get_result_request = rp_pb2.GetResultRequest()
    get_result_request.request_id = request_id
    get_result_request = stub.GetResult(get_result_request, metadata=metadata)
    print("Request result: ", rp_pb2.RequestStatus.Name(get_result_request.status))
    if get_result_request.status == rp_pb2.SUCCESS:
        client = x509.load_der_x509_certificate(get_result_request.signature.certificate_client, default_backend())
        issuer = x509.load_der_x509_certificate(get_result_request.signature.certificate_issuer, default_backend())
        root = x509.load_pem_x509_certificate(str.encode(root_cert), default_backend())
        ocsp = x509.ocsp.load_der_ocsp_response(get_result_request.signature.ocsp_response)

        # verify issuer's certificate
        root.public_key().verify(issuer.signature, issuer.tbs_certificate_bytes, ec.ECDSA(hashes.SHA384()))

        # verify client's certificate
        issuer.public_key().verify(client.signature, client.tbs_certificate_bytes, ec.ECDSA(hashes.SHA384()))

        verify_ocsp(ocsp, client, issuer)

        # verify signature
        client.public_key().verify(get_result_request.signature.sign, get_result_request.signature.signature_data, ec.ECDSA(hashes.SHA256()))

        # check data that client has signed
        signature_data = rp_pb2.GetResultResponse.SignatureResult.SignatureData()
        signature_data.ParseFromString(get_result_request.signature.signature_data)

        # verify that signature data is valid
        if signature_data.email != email or signature_data.title != create_request.title:
            raise Exception("invalid signature data")

        print("signature verification succeed")



def main():
    with grpc.secure_channel("api.staging.autheid.com", grpc.ssl_channel_credentials()) as channel:
        stub = rp_pb2_grpc.RequestsStub(channel)

        send_request(stub)

if __name__ == "__main__":
    main()
