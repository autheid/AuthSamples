from grpc_tools import protoc

protoc.main((
    '',
    '--proto_path=.',
    '--python_out=.',
    '--grpc_python_out=.',
    'rp.proto',
))
