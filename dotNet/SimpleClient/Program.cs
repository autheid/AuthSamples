using System;
using Grpc.Core;
using Autheid.Rp;
using System.Threading.Tasks;
using System.IO;

namespace SimpleClient
{
    class Program
    {
        public static void Main(string[] args)
        {
            var apiKey = "Pj+Q9SsZloftMkmE7EhA8v2Bz1ZC9aOmUkAKTBW9hagJ";
            var email = "test3@verified-fast.com";

            AsyncAuthInterceptor authInterceptor = new AsyncAuthInterceptor((context, metadata) =>
            {
                metadata.Add("Authorization", "Bearer " + apiKey);
                return Task.FromResult(0);
            });

            var channelCredentials = ChannelCredentials.Create(new SslCredentials(),
               CallCredentials.FromInterceptor(authInterceptor));

            Channel channel = new Channel("api.staging.autheid.com", channelCredentials);

            var client = new Requests.RequestsClient(channel);

            var request = new CreateRequest();
            request.Email = email;
            request.Title = "test";
            request.Type = RequestType.Kyc;
            request.Kyc = new CreateRequest.Types.KYC();
            request.Kyc.FilesFormat = FileFormat.Embedded;

            var reply = client.Create(request);
            Console.WriteLine("Request ID: " + reply.RequestId);

            var resultRequest = new GetResultRequest();
            resultRequest.RequestId = reply.RequestId;

            var resultReply = client.GetResult(resultRequest);
            Console.WriteLine("Status: " + resultReply.Status);

            if (resultReply.Status == RequestStatus.Success)
            {
                Console.WriteLine("Fist Name: " + resultReply.Kyc.Identification.FirstName);
                Console.WriteLine("Last Name: " + resultReply.Kyc.Identification.LastName);
                Console.WriteLine("File size: " + resultReply.Kyc.KycPdf.Embedded.Length);

                // Create a new file     
                using (FileStream fs = System.IO.File.Create("kyc.pdf"))
                {
                    fs.Write(resultReply.Kyc.KycPdf.Embedded.ToByteArray(), 0, resultReply.Kyc.KycPdf.Embedded.Length);
                }
            }

            channel.ShutdownAsync().Wait();
            Console.WriteLine("Press any key to exit...");
            Console.ReadKey();
        }
    }
}
