package com.bkic.lymenglong.audiobookbkic.handleLists.favorite;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.bkic.lymenglong.audiobookbkic.account.login.Session;
import com.bkic.lymenglong.audiobookbkic.checkInternet.ConnectivityReceiver;
import com.bkic.lymenglong.audiobookbkic.checkInternet.MyApplication;
import com.bkic.lymenglong.audiobookbkic.customizes.CustomActionBar;
import com.bkic.lymenglong.audiobookbkic.database.DBHelper;
import com.bkic.lymenglong.audiobookbkic.download.DownloadReceiver;
import com.bkic.lymenglong.audiobookbkic.handleLists.utils.Book;
import com.bkic.lymenglong.audiobookbkic.utils.Const;
import com.bkic.lymenglong.audiobookbkic.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static com.bkic.lymenglong.audiobookbkic.utils.Const.DB_NAME;
import static com.bkic.lymenglong.audiobookbkic.utils.Const.DB_VERSION;
import static com.bkic.lymenglong.audiobookbkic.utils.Const.HttpURL_API;

public class ListFavorite
        extends AppCompatActivity
        implements
                ListFavoriteImp,
                ConnectivityReceiver.ConnectivityReceiverListener,
                DownloadReceiver.DownloadReceiverListener {

    private static final String TAG = "ListFavorite";
    PresenterShowListFavorite presenterShowList = new PresenterShowListFavorite(this);
    PresenterUpdateFavorite presenterUpdateFavorite = new PresenterUpdateFavorite(this);
    private RecyclerView listChapter;
    private View imRefresh;
    private FavoriteAdapter favoriteAdapter;
    private String menuTitle;
    private Activity activity = ListFavorite.this;
    private Session session;
    private ProgressBar progressBar;
    private DBHelper dbHelper;
    private ArrayList <Book> list = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_list);
        initIntentFilter();
        ViewCompat.setImportantForAccessibility(getWindow().findViewById(R.id.tvToolbar), ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
        getDataFromIntent();
        setTitle(menuTitle);
        initView();
        initDatabase();
        initObject();
    }

    //region BroadCasting
    //connectionReceiver
    private IntentFilter intentFilter;
    private ConnectivityReceiver receiver;
    //downloadReceiver
    private IntentFilter filter;
    private DownloadReceiver downloadReceiver;

    private void initIntentFilter() {
        intentFilter = new IntentFilter();
        intentFilter.addAction(CONNECTIVITY_ACTION);
        receiver = new ConnectivityReceiver();
        //set filter to only when download is complete and register broadcast receiver
        filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        downloadReceiver = new DownloadReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // register receiver
        registerReceiver(receiver, intentFilter);
        registerReceiver(downloadReceiver, filter);
        // register status listener
        MyApplication.getInstance().setConnectivityListener(this);
        MyApplication.getInstance().setDownloadListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //unregister receiver
        unregisterReceiver(receiver);
        unregisterReceiver(downloadReceiver);
    }

    @Override
    public void onNetworkConnectionChanged(boolean isConnected) {

    }

    @Override
    public void onDownloadCompleted(long downloadId) {

    }
    //endregion

    /**
     * Lấy dữ liệu thông qua intent
     */
    private void getDataFromIntent() {
        menuTitle = getIntent().getStringExtra("MenuTitle");
//        int idMenu = getIntent().getIntExtra("idHome", -1);
    }

    /**
     * Khai báo các view và khởi tạo giá trị
     */
    private void initView() {
        session = new Session(activity);
        progressBar = findViewById(R.id.progressBar);
        imRefresh = findViewById(R.id.imRefresh);
        CustomActionBar actionBar = new CustomActionBar();
        actionBar.eventToolbar(this, menuTitle, true);
        listChapter = findViewById(R.id.listView);
    }

    private void initDatabase() {
        dbHelper = new DBHelper(this,DB_NAME ,null,DB_VERSION);
    }

    private void GetCursorData() {
        Cursor cursor;
        list.clear();
        cursor = dbHelper.GetData("SELECT * FROM favorite WHERE BookRemoved = '"+Const.BOOK_NOT_REQUEST_REMOVE_SYNCED_WITH_SERVER+"'");
        while (cursor.moveToNext()) {
            int bookId = cursor.getInt(0);
            String bookTitle = cursor.getString(1);
            String bookImage = cursor.getString(2);
            int bookLength = cursor.getInt(3);
            String bookAuthor = cursor.getString(4);

            list.add(new Book(bookId,bookTitle,bookImage,bookLength,bookAuthor));
        }
        cursor.close();
        favoriteAdapter.notifyDataSetChanged();
        dbHelper.close();
    }

    private void initObject() {
        imRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(ConnectivityReceiver.isConnected()) {
                    RemoveFavoriteDataInSQLite();
                    SyncRemoveBooks();
                    RequestLoadingData();
                } else Toast.makeText(activity, getString(R.string.message_internet_not_connected), Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayoutManager mLinearLayoutManager = new LinearLayoutManager(this);
        listChapter.setLayoutManager(mLinearLayoutManager);
        favoriteAdapter = new FavoriteAdapter(activity, list);
        listChapter.setAdapter(favoriteAdapter);
        GetCursorData();
        //get data from json parsing
        if(list.isEmpty()) {
            if(ConnectivityReceiver.isConnected()) RequestLoadingData();
            else progressBar.setVisibility(View.GONE);
        } else progressBar.setVisibility(View.GONE);
    }

    private void SyncRemoveBooks() {
        Cursor cursor = dbHelper.GetData(
                "SELECT BookId FROM bookFavoriteSyncs WHERE BookRemoved = '"+Const.BOOK_REQUEST_REMOVE_WITH_SERVER+"'"
        );
        while (cursor.moveToNext()){
            //calling the method to save the unsynced books to MySQL server
            presenterUpdateFavorite.RequestToRemoveBookById
                    (
                            String.valueOf(session.getUserIdLoggedIn()),
                            String.valueOf(cursor.getInt(0))
                    );
        }
        cursor.close();
    }

    private void RequestLoadingData() {
        int userId =session.getUserIdLoggedIn();
        HashMap<String,String> ResultHash = new HashMap<>();
        String keyPost = "json";
        String valuePost =
                "{" +
                        "\"Action\":\"getFavourite\", " +
                        "\"UserId\":\""+userId+"\"" +
                        "}";
        ResultHash.put(keyPost,valuePost);
        presenterShowList.GetSelectedResponse(activity, ResultHash,HttpURL_API);
    }

    private void SetInsertTableData(Book arrayModel) {
        String INSERT_DATA =
                "INSERT INTO favorite VALUES" +
                "(" +
                        "'"+arrayModel.getId()+"'," +
                        "'"+arrayModel.getTitle()+"'," +
                        "'"+arrayModel.getUrlImage()+"'," +
                        "'"+arrayModel.getLength()+"'," +
                        "'"+arrayModel.getAuthor()+"', " +
                        "'"+Const.BOOK_SYNCED_WITH_SERVER+"', " + //BookSync is equal 1, It means that book is already store in server
                        "'"+Const.BOOK_NOT_REQUEST_REMOVE_SYNCED_WITH_SERVER+"'"+
                ");";
        dbHelper.QueryData(INSERT_DATA);
    }

    private void SetUpdateTableData(Book arrayModel) {
        String UPDATE_DATA = "UPDATE favorite SET " +
                "BookTitle = '"+arrayModel.getTitle()+"', " +
                "BookImage = '"+arrayModel.getUrlImage()+"', " +
                "BookLength = '"+arrayModel.getLength()+"', " +
                "BookAuthor = '"+arrayModel.getAuthor()+"', " +
                "BookSync = '"+Const.BOOK_SYNCED_WITH_SERVER+"' " +
                "WHERE " +
                "BookId = '"+arrayModel.getId()+"'; ";
        dbHelper.QueryData(UPDATE_DATA);
    }

    @Override
    public void CompareDataPhoneWithServer(JSONArray jsonArray) { }

    @Override
    public void SetTableSelectedData(JSONObject jsonObject) throws JSONException {

        Book tempModel = new Book();
        tempModel.setId(Integer.parseInt(jsonObject.getString("BookId")));
        tempModel.setTitle(jsonObject.getString("BookTitle"));
        tempModel.setUrlImage(jsonObject.getString("BookImage"));
        tempModel.setLength(Integer.parseInt(jsonObject.getString("BookLength")));
        tempModel.setAuthor(jsonObject.getString("Author"));

        try {
            SetInsertTableData(tempModel);
        } catch (Exception e) {
            SetUpdateTableData(tempModel);
        }
    }

    @Override
    public void ShowListFromSelected() {
        progressBar.setVisibility(View.GONE);
        GetCursorData();
        Log.d(TAG, "onPostExecute: "+ menuTitle);
    }

    @Override
    public void LoadListDataFailed(String jsonMessage){
//        RemoveFavoriteDataInSQLite();
        GetCursorData();
        Log.d(TAG, "LoadListDataFailed: "+ jsonMessage);
    }

    private void RemoveFavoriteDataInSQLite() {
        String DELETE_DATA = "DELETE FROM favorite";
        dbHelper.QueryData(DELETE_DATA);
        dbHelper.close();
    }

    @Override
    public void RemoveFavoriteSuccess(String message) {
        Log.d(TAG, "RemoveFavoriteSuccess: "+message);
    }

    @Override
    public void RemoveFavoriteFailed(String message) {
        Log.e(TAG, "RemoveFavoriteFailed: "+message);
    }

    @Override
    public void RemoveAllFavoriteSuccess(String message) {
        Log.d(TAG, "RemoveAllFavoriteSuccess: "+message);
    }

    @Override
    public void RemoveAllFavoriteFailed(String message) {
        Log.e(TAG, "RemoveAllFavoriteFailed: "+message);
    }

}
