//
//  ViewController.swift
//  com.autheid.AuthAppDemo
//
//  Created by admin0 on 12/3/18.
//  Copyright © 2018 Admin. All rights reserved.
//

import UIKit

class ViewController: UIViewController {

    @IBOutlet weak var buttonClick: UIButton!
    @IBOutlet weak var textLogin: UITextField!
    @IBOutlet weak var textViewLog: UITextView!

    let apiKey = "Pj+Q9SsZloftMkmE7EhA8v2Bz1ZC9aOmUkAKTBW9hagJ"
    let baseUrl = "https://api.staging.autheid.com/v1"
        
    override func viewDidLoad() {
        super.viewDidLoad()
    }

    @IBAction func testClicked(_ sender: Any) {
        let url = URL(string: "autheid://request?url=autheiddemo%3A%2F%2Ftest")!
        
        // prepare json data
        let json: [String: Any] = [
            "email": textLogin.text!,
            "title": "Test Login",
            "type": "AUTHENTICATION"
        ]
        
        let jsonData = try? JSONSerialization.data(withJSONObject: json)
        
        // create post request
        let url2 = URL(string: baseUrl + "/requests")!
        var request = URLRequest(url: url2)
        request.setValue("Bearer " + apiKey, forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpMethod = "POST"
        
        // insert json data to the request
        request.httpBody = jsonData
        
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            guard let data = data, error == nil else {
                print(error?.localizedDescription ?? "No data")
                return
            }
            let responseJSON = try? JSONSerialization.jsonObject(with: data, options: [])
            if let responseJSON = responseJSON as? [String: Any] {
                print(responseJSON)
                let requestId = responseJSON["request_id"] as? String ?? ""
                if !requestId.isEmpty {
                    DispatchQueue.main.async() {
                        print("requestId: \(requestId)")
                        self.textViewLog.text = "requestId: \(requestId)\n" + self.textViewLog.text
                        self.readResult(requestId: requestId)

                        print("open", url)
                        if #available(iOS 10.0, *) {
                            UIApplication.shared.open(url, options: [:], completionHandler: nil)
                        } else {
                            UIApplication.shared.openURL(url)
                        }
                    }
                } else if let errorMsg = responseJSON["message"] as? String {
                    DispatchQueue.main.async() {
                        print("errorMsg: \(errorMsg)")
                        self.textViewLog.text = "errorMsg: \(errorMsg)\n" + self.textViewLog.text
                    }
                }
            }
        }
        task.resume()
    }
    
    func readResult(requestId: String) {
        // create post request
        let url = URL(string: "\(baseUrl)/requests/\(requestId)")!
        var request = URLRequest(url: url)
        request.setValue("Bearer " + apiKey, forHTTPHeaderField: "Authorization")
        request.httpMethod = "GET"
        
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            guard let data = data, error == nil else {
                print(error?.localizedDescription ?? "No data")
                return
            }
            let responseJSON = try? JSONSerialization.jsonObject(with: data, options: [])
            if let responseJSON = responseJSON as? [String: Any] {
                print(responseJSON)
                if let status = responseJSON["status"] as? String {
                    DispatchQueue.main.async() {
                        print("status: \(status)")
                        self.textViewLog.text = "status: \(status)\n" + self.textViewLog.text
                    }
                }
            }
        }
        
        task.resume()
    }
}

