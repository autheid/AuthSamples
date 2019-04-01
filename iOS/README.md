# Auth eID iOS native client example

This sample app makes request and opens Auth eID using registered iOS "app link" (`autheid://request`).

Because iOS does not provide ability to close app the client should provide link (in additional `url` parameter) that will be opened after sign:

```
let url = URL(string: "autheid://request?url=autheiddemo%3A%2F%2Ftest")!
```

This URL should be registered for the client's app (here it's `autheiddemo`) using Xcode or editing `Info.plist` file:

```
<plist version="1.0">
<dict>
	<key>CFBundleURLTypes</key>
	<array>
		<dict>
			<key>CFBundleTypeRole</key>
			<string>Viewer</string>
			<key>CFBundleURLSchemes</key>
			<array>
				<string>autheiddemo</string>
			</array>
		</dict>
	</array>
	...
</dict>
</plist>
```
