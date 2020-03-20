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
    @IBOutlet weak var textViewLog: UITextView!

    let apiKey = "Pj+GIg2/l7ZKmicZi37+1giqKJ1WH3Vt8vSSxCuvPkKD"
    let baseUrl = "https://api.staging.autheid.com/v1"

    var requestId: String = ""

    override func viewDidLoad() {
        super.viewDidLoad()
        NotificationCenter.default.addObserver(self, selector: #selector(checkRequest), name: UIApplication.didBecomeActiveNotification, object: nil)
    }

    @objc func checkRequest() {
        if !requestId.isEmpty {
            self.readResult(requestId: requestId)
        }
    }

    func addMessage(_ message: String) {
        print(message)
        self.textViewLog.text = message + "\n" + self.textViewLog.text
    }

    @IBAction func testClicked(_ sender: Any) {
        // prepare json data
        let json: [String: Any] = [
            "use_local_account": true,
            "title": "Test Login",
            "type": "AUTHENTICATION",
            "timeout_seconds": 30,
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
                self.addMessage(error?.localizedDescription ?? "No data")
                return
            }
            let responseJSON = try? JSONSerialization.jsonObject(with: data, options: [])
            if let responseJSON = responseJSON as? [String: Any] {
                print(responseJSON)
                self.requestId = responseJSON["request_id"] as? String ?? ""
                if !self.requestId.isEmpty {
                    DispatchQueue.main.async() {
                        self.addMessage("requestId: \(self.requestId)")

                        let autheidUrl = "autheid://autheid.com/app/requests/?callback=autheiddemo%3A%2F%2Ftest&request_id=" + self.requestId
                        let u = URL(string: autheidUrl)!

                        print("open", autheidUrl)
                        if #available(iOS 10.0, *) {
                            UIApplication.shared.open(u, options: [:]) {
                                (success) in
                                if (!success) {
                                    self.addMessage("Auth eID is not installed")
                                }
                            }
                        } else {
                            UIApplication.shared.openURL(u)
                        }
                    }
                } else if let errorMsg = responseJSON["message"] as? String {
                    DispatchQueue.main.async() {
                        self.addMessage("request failed: \(errorMsg)")
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
                self.addMessage(error?.localizedDescription ?? "Unknown error")
                return
            }
            let responseJSON = try? JSONSerialization.jsonObject(with: data, options: [])
            if let responseJSON = responseJSON as? [String: Any] {
                print(responseJSON)
                if let status = responseJSON["status"] as? String {
                    let email = responseJSON["email"]
                    DispatchQueue.main.async() {
                        var message = "status: \(status)"
                        if email != nil {
                            message += ", email: \(email!)"
                        }
                        self.addMessage(message)
                        self.requestId = ""
                    }
                } else {
                    self.addMessage("Invalid JSON")
                }
            }
        }

        task.resume()
    }
}

