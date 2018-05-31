package com.bkic.lymenglong.audiobookbkic.main;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import com.bkic.lymenglong.audiobookbkic.https.HttpServicesClass;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PresenterMain implements PresenterMainImp {
    private MainActivity mainActivity;
//    private ProgressDialog pDialog;

    public PresenterMain(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @Override
    public void GetHttpResponse(String httpUrl) {
        new GetHttpResponse(mainActivity).execute(httpUrl);
    }

    //region JSON parse class started from here.
    @SuppressLint("StaticFieldLeak")
    private class GetHttpResponse extends AsyncTask<String, Void, Void> {

        Context context;
        String JSonResult;

        GetHttpResponse(Context context) {
            this.context = context;
        }

        @Override
        protected Void doInBackground(String... httpUrl) {
            // Passing HTTP URL to HttpServicesClass Class.

            HttpServicesClass httpServicesClass = new HttpServicesClass(httpUrl[0]);
            try {
                httpServicesClass.ExecutePostRequest();

                if (httpServicesClass.getResponseCode() == 200) {
                    JSonResult = httpServicesClass.getResponse();

                    if (JSonResult != null) {
                        JSONArray jsonArray;

                        try {
                            jsonArray = new JSONArray(JSonResult);

                            JSONObject jsonObject;

                            for (int i = 0; i < jsonArray.length(); i++) {

                                jsonObject = jsonArray.getJSONObject(i);

                                // Adding data TO IdList Array.
                                mainActivity.SetMenuData(jsonObject);
                            }
                        } catch (JSONException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                // TODO Auto-generated catch block0
                Toast.makeText(context, httpServicesClass.getErrorMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
//            pDialog = ProgressDialog.show(mainActivity, "Load Data", "Please wait...", true, true);
        }


        @Override
        protected void onPostExecute(Void result) {
//            pDialog.dismiss();
            mainActivity.ShowListMenu();

        }
    }
    //endregion
}
