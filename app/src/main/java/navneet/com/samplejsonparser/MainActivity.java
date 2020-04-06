package navneet.com.samplejsonparser;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private DataAdapter dataAdapter;
    private ArrayList<CarModel> carModels = new ArrayList<>();
    private RecyclerView mRecyclerView;
    private ProgressBar mProgressBar;
    private final String TAG = "MainActivity";
    private final String BASE_URL = "https://navneet7k.github.io/";
    public static final String HEADER_CACHE_CONTROL = "Cache-Control";
    public static final String HEADER_PRAGMA = "Pragma";
    private final Long cacheSize = (long) (5 * 1024 * 1024);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.VISIBLE);
        mRecyclerView = (RecyclerView) findViewById(R.id.cars_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        parseJson();
    }

    private void parseJson() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RequestInterface request = retrofit.create(RequestInterface.class);
        Call<List<CarModel>> call1 = request.getJson();
        call1.enqueue(new Callback<List<CarModel>>() {
            @Override
            public void onResponse(Call<List<CarModel>> call, Response<List<CarModel>> response) {
                mProgressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    carModels = new ArrayList<>(response.body());
                    Log.d(TAG, "onResponse: carModels.size  " + carModels.size());
                    dataAdapter = new DataAdapter(carModels, MainActivity.this);
                    mRecyclerView.setAdapter(dataAdapter);
                }
            }

            @Override
            public void onFailure(Call<List<CarModel>> call, Throwable t) {
                Log.d(TAG, "onFailure: " + t.getLocalizedMessage());
                mProgressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Oops! Something went wrong!", Toast.LENGTH_SHORT).show();
            }

        });
    }

    private OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .cache(cache())
                .addInterceptor(httpLoggingInterceptor()) // used if network off OR on
                .addNetworkInterceptor(networkInterceptor()) // only used when network is on
                .addInterceptor(offlineInterceptor())
                .build();
    }

    private Cache cache() {
        try {
            Log.d(TAG, "Using Internal Cache");
            return new Cache(new File(MyApplication.getInstance().getExternalCacheDir(), "externalCacheId"), cacheSize);
        } catch (Exception e) {
            Log.d(TAG, "Error cache: " + e.getLocalizedMessage() + "\nUsing External Cache");
            File httpCacheDirectory = new File(getCacheDir(), "internalCacheId");
            return new Cache(httpCacheDirectory, cacheSize);
        }
    }

    //This interceptor will be called both if the network is available and if the network is not available
    private Interceptor offlineInterceptor() {
        return new Interceptor() {
            @Override
            public okhttp3.Response intercept(Chain chain) throws IOException {
                Log.d(TAG, "offline interceptor: called.");
                okhttp3.Request request = chain.request();

                // prevent caching when network is on. For that we use the "networkInterceptor"
                if (!hasNetwork()) {
                    CacheControl cacheControl = new CacheControl.Builder()
                            .maxStale(7, TimeUnit.DAYS)
                            .build();

                    request = request.newBuilder()
                            .removeHeader(HEADER_PRAGMA)
                            .removeHeader(HEADER_CACHE_CONTROL)
                            .cacheControl(cacheControl)
                            .build();
                }

                return chain.proceed(request);
            }
        };
    }

    public boolean hasNetwork() {
        return isNetworkConnected();
    }

    public boolean isNetworkConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }

    //This interceptor will be called ONLY if the network is available
    private Interceptor networkInterceptor() {
        return new Interceptor() {
            @Override
            public okhttp3.Response intercept(Chain chain) throws IOException {
                Log.d(TAG, "network interceptor: called.");

                okhttp3.Response response = chain.proceed(chain.request());

                CacheControl cacheControl = new CacheControl.Builder()
                        .maxAge(5, TimeUnit.SECONDS)
                        .build();

                return response.newBuilder()
                        .removeHeader(HEADER_PRAGMA)
                        .removeHeader(HEADER_CACHE_CONTROL)
                        .header(HEADER_CACHE_CONTROL, cacheControl.toString())
                        .build();
            }
        };
    }

    private HttpLoggingInterceptor httpLoggingInterceptor() {
        HttpLoggingInterceptor httpLoggingInterceptor =
                new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
                    @Override
                    public void log(String message) {
                        Log.d(TAG, "httpLoggingInterceptor: http log: " + message);
                    }
                });
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        return httpLoggingInterceptor;
    }

}
