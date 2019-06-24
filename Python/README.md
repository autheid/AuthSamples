## Install libs
Install gRPC runtime and build tools:
`$ pip install grpcio grpcio-tools`

For signature verification test (tested with cryptography 2.6.1 and 2.7):
`$ pip install cryptography`

## Generate stubs:
`$ python run_codegen.py`

## Run:
`$ python simple_client.py`
`$ python signature_test.py`

Both python2 and python3 are supported (for python3 you might need to use `python3`/`pip3`).
