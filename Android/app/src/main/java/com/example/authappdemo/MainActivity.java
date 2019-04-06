package com.example.authappdemo;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity
{
  final static String TAG = "AuthAppDemo";

  final static String apiKey = "Pj+GIg2/l7ZKmicZi37+1giqKJ1WH3Vt8vSSxCuvPkKD";
  final static String baseUrl = "https://api.staging.autheid.com/v1";

  static private RequestQueue requestQueue_;

  static private Map<String, String> getHeaders() {
    Map<String, String>  params = new HashMap<String, String>();
    params.put("Authorization", "Bearer " + apiKey);
    return params;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    requestQueue_ = Volley.newRequestQueue(this);
  }

  void showMessage(String msg) {
    Log.i(TAG, msg);

    TextView textViewStatus = findViewById(R.id.textViewStatus);

    textViewStatus.setText(msg + "\n" + textViewStatus.getText().toString());
  }

  void showError(Exception e) {
    showMessage("error: " + e.toString());
  }

  void queryRequest(String requestId) {
    JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, baseUrl + "/requests/" + requestId, null,
        new Response.Listener<JSONObject>() {
          @Override
          public void onResponse(JSONObject response) {
            try {
              String status = response.getString("status");
              String msg = "status: " + status;
              if (status.equals("SUCCESS")) {
                String email = response.getString("email");
                msg += ", email: " + email;
              }
              showMessage(msg);
            } catch (JSONException e) {
              showError(e);
            }
          }
        },
        new Response.ErrorListener() {
          @Override
          public void onErrorResponse(VolleyError e) {
            showError(e);
          }
        }
    ) {
      @Override
      public Map<String, String> getHeaders() throws AuthFailureError
      {
        return MainActivity.getHeaders();
      }
    };

    request.setRetryPolicy(new DefaultRetryPolicy(
        60 * 1000,
        DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

    requestQueue_.add(request);
  }

  void postData() {
    JSONObject json = new JSONObject();
    try {
      json.put("title", "Test Login");
      json.put("type", "AUTHENTICATION");
      json.put("use_local_account", true);
    } catch (JSONException e) {
      showMessage("error: " + e.toString());
    }

    JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, baseUrl + "/requests", json,
        new Response.Listener<JSONObject>() {
          @Override
          public void onResponse(JSONObject response) {
            try {
              boolean success = response.getBoolean("success");
              String requestId = response.getString("request_id");
              showMessage("requestId: " + requestId);
              queryRequest(requestId);

              String url = "autheid://autheid.com/app/requests/?request_id=" + requestId;
              Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
              try {
                startActivity(browserIntent);
              } catch (ActivityNotFoundException e) {
                showMessage("AutheID is not installed");
              }
            } catch (JSONException e) {
              showError(e);
            }
          }
        },
        new Response.ErrorListener() {
          @Override
          public void onErrorResponse(VolleyError e) {
            showError(e);
          }
        }
    ) {
      @Override
      public Map<String, String> getHeaders() throws AuthFailureError
      {
        return MainActivity.getHeaders();
      }
    };
    requestQueue_.add(request);

  }
  public void onClickTest(View w)
  {
    postData();
  }
}
