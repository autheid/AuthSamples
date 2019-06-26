from __future__ import print_function

import base64
import json
import datetime
import requests

from cryptography import x509
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes

api_key = "Pj+Q9SsZloftMkmE7EhA8v2Bz1ZC9aOmUkAKTBW9hagJ"
email = "test@example.com"
title = "Test"

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

def send_request():
    headers={"Authorization": "Bearer " + api_key}
    data = {
        "title": title,
        "type": "SIGNATURE",
        "email": email,
    };
    create_response = requests.post('https://api.staging.autheid.com/v1/requests', json=data, headers=headers).json()
    request_id = create_response["request_id"]
    print("Request ID: ", request_id)

    result_response = requests.get('https://api.staging.autheid.com/v1/requests/' + request_id, headers=headers).json()
    print("Request result: ", result_response["status"])

    if result_response["status"] == "SUCCESS":
        signature = result_response["signature"]
        client = x509.load_der_x509_certificate(base64.b64decode(signature["certificate_client"]), default_backend())
        issuer = x509.load_der_x509_certificate(base64.b64decode(signature["certificate_issuer"]), default_backend())
        root = x509.load_pem_x509_certificate(str.encode(root_cert), default_backend())
        ocsp = x509.ocsp.load_der_ocsp_response(base64.b64decode(signature["ocsp_response"]))

        # verify issuer's certificate
        root.public_key().verify(issuer.signature, issuer.tbs_certificate_bytes, ec.ECDSA(hashes.SHA384()))

        # verify client's certificate
        issuer.public_key().verify(client.signature, client.tbs_certificate_bytes, ec.ECDSA(hashes.SHA384()))

        verify_ocsp(ocsp, client, issuer)

        # verify signature
        signed_data_binary = base64.b64decode(signature["signature_data"])
        signed_data_sign = base64.b64decode(signature["sign"])
        client.public_key().verify(signed_data_sign, signed_data_binary, ec.ECDSA(hashes.SHA256()))

        # check data that client has signed
        signed_data = json.loads(signed_data_binary)

        # verify that signature data is valid
        if signed_data["email"] != email or signed_data["title"] != title:
            raise Exception("invalid signature data")

        print("signature verification succeed")


def main():
    send_request()

if __name__ == "__main__":
    main()
