# 前言 

网络请求在项目开发中必不可少，封装的好坏很大程度上影响的项目质量，本篇博文 [walid](http://blog.csdn.net/walid1992) 与大家分享一下本人的愚见与rxretrofit框架讲解~

# 劣质请求框架的表现

1.与业务逻辑严重耦合

2.存在很多复杂冗余代码

3.写法不够傻瓜

4.请求统一处理不佳

  ...
  
# rxretrofit 框架介绍

## 技术概要

rxretrofit库采用了rxjava 2.0+ + retrofit 2.0 进行整合封装， [retrofit2.0](http://blog.csdn.net/walid1992/article/details/52421399) 与 [rxjava](http://blog.csdn.net/walid1992/article/details/52426040) 在之前文章中都有所介绍，相信大家也都会有所了解，rxjava 与 retrofit的思想就不和大家进行过多的解读了，长话短说，我们开始吧~


## 依赖module

```
dependencies { 
    // ... 省略部分依赖
    // rxjava
    api 'io.reactivex.rxjava2:rxjava:2.1.4'
    api 'io.reactivex.rxjava2:rxandroid:2.0.1'
    api 'com.tbruyelle.rxpermissions2:rxpermissions:0.9.4@aar'
    api 'com.squareup.okhttp3:okhttp:3.8.1'
    api 'com.squareup.okhttp3:logging-interceptor:3.6.0'
    api 'com.squareup.retrofit2:converter-gson:2.2.0'
    api 'com.squareup.retrofit2:adapter-rxjava2:2.2.0'
}
```

## 目录结构

<img src="http://img.blog.csdn.net/20161021130225903" width = "798" height = "948" alt="Rxretrofit 框架目录结构" align=center />

**简单介绍下文件的作用，这里没有优先级，直接从上至下 ：**

1. RetrofitParams ： 
    配置参数，包括超时时间、转换器、拦截器等
2. ExceptionCode ：
    http异常代码
3. ServerResultException：
自定义server异常
4. ICodeVerify :
    codehi合法校验接口，用于服务器code异常校验
5. IHttpCallback :
    rxjava 订阅callback
6. IHttpCancelListener :
    http 请求取消接口
7. IHttpResult :
    http数据返回接口，统一规范
8. SimpleHttpCallback :
    IHttpResult的实现类
9. RxRetrogitLog :
    log 日志工具类
10. HttpManager :
    http网络请求管理
11. HttpSubscriber :
    http 订阅处理

根据目录结构大家对项目应该有了一个整体的认识。

## 代码解析

库中的代码整体比较简单，草民这里挑出两个重要的类来进行介绍吧~

### HttpManager

```
/**
 * Author   : walid
 * Data     : 2016-08-18  15:58
 * Describe : http 管理类
 */
public class HttpManager {

    private Retrofit retrofit;
    private ICodeVerify codeVerify;

    private HttpManager() {
    }

    public static HttpManager getInstance() {
        return HttpManager.SingletonHolder.instance;
    }

    private static class SingletonHolder {
        static HttpManager instance = new HttpManager();
    }

    public void create(String baseUrl, ICodeVerify codeVerify, RetrofitParams params) {
        this.codeVerify = codeVerify;
        Converter.Factory converterFactory = params.getConverterFactory();
        CallAdapter.Factory callAdapterFactory = params.getCallAdapterFactor();
        retrofit = new Retrofit.Builder().baseUrl(baseUrl)
                .addConverterFactory(converterFactory != null ? converterFactory : GsonConverterFactory.create(new GsonBuilder().create()))
                .addCallAdapterFactory(callAdapterFactory != null ? callAdapterFactory : RxJava2CallAdapterFactory.create())
                .client(createClient(params))
                .build();
    }

    private OkHttpClient createClient(RetrofitParams params) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // 设置超时
        int connectTimeoutSeconds = params.getConnectTimeoutSeconds();
        if (connectTimeoutSeconds > 0) {
            builder.connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS);
        }

        int readTimeoutSeconds = params.getReadTimeoutSeconds();
        if (readTimeoutSeconds > 0) {
            builder.readTimeout(readTimeoutSeconds, TimeUnit.SECONDS);
        }

        int writeTimeoutSeconds = params.getWriteTimeoutSeconds();
        if (writeTimeoutSeconds > 0) {
            builder.writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS);
        }

        // Log信息拦截器
        builder.addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY));

        ArrayList<Interceptor> interceptors = params.getInterceptors();
        if (interceptors != null && interceptors.size() > 0) {
            for (Interceptor interceptor : interceptors) {
                builder.addInterceptor(interceptor);
            }
        }
        return builder.build();
    }

    public <ApiType> ApiType getApiService(Class<ApiType> type) {
        return retrofit.create(type);
    }

    public <T, Result extends IHttpResult<T>> HttpSubscriber<T> toSubscribe(Observable<Result> observable, Context context, IHttpCallback<T> listener) {
        return toSubscribe(observable, new HttpSubscriber<>(context, listener));
    }

    public <T, Result extends IHttpResult<T>> HttpSubscriber<T> toSubscribe(Observable<Result> observable, Context context, IHttpCallback<T> listener, boolean isShowToast) {
        return toSubscribe(observable, new HttpSubscriber<>(context, listener, isShowToast));
    }

    public <T, Result extends IHttpResult<T>> HttpSubscriber<T> toSubscribe(Observable<Result> observable, HttpSubscriber<T> httpSubscriber) {
        Observable<T> observableNew = observable.map(new Function<Result, T>() {
            @Override
            public T apply(Result result) throws Exception {
                if (result == null) {
                    throw new IllegalStateException("数据为空~");
                }
                RxRetrogitLog.d(result.toString());
                int code = result.getCode();
                if (!codeVerify.checkValid(result.getCode())) {
                    throw new ServerResultException(code, codeVerify.formatCodeMessage(code, result.getMsg()));
                }
                return result.getData();
            }
        });
        observableNew.subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(httpSubscriber);
        return httpSubscriber;
    }

}


```

重要处理：

1. create :
rxretrofit库的初始化，建议在Application中进行初始化，通过参数可以清晰的得知，传入了baseurl、code校验与params，从而设置OkHttpClient。
2. getApiService :
获取api的实例对象。
3. toSubscribe :
订阅网络请求，调用此方法进行网络请求，在网络请求发起时创建了HttpSubscriber对象，进行rxjava的事件订阅，进行统一处理。

### HttpSubscriber

```
/**
 * Author   : walid
 * Data     : 2016-08-18  15:59
 * Describe : http 观察者(订阅者)
 */
public class HttpSubscriber<T> implements IHttpCancelListener, Observer<T> {

    private static final String TAG = "HttpSubscriber";

    //对应HTTP的状态码
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int REQUEST_TIMEOUT = 408;
    private static final int INTERNAL_SERVER_ERROR = 500;
    private static final int BAD_GATEWAY = 502;
    private static final int SERVICE_UNAVAILABLE = 503;
    private static final int GATEWAY_TIMEOUT = 504;

    private Context context;
    private IHttpCallback<T> httpCallback;
    private boolean showError;

    public HttpSubscriber(Context context, IHttpCallback<T> httpCallback) {
        this(context, httpCallback, true);
    }

    public HttpSubscriber(Context context, IHttpCallback<T> httpCallback, boolean showError) {
        this.context = context;
        this.httpCallback = httpCallback;
        this.showError = showError;
    }

    // 对错误进行统一处理
    @Override
    public void onError(Throwable e) {

        Throwable throwable = e;
        //获取最根源的异常
        while (throwable.getCause() != null) {
            e = throwable;
            throwable = throwable.getCause();
        }

        //HTTP错误
        if (e instanceof HttpException) {
            HttpException httpException = (HttpException) e;
            switch (httpException.code()) {
                //权限错误，需要实现
                case UNAUTHORIZED:
                case FORBIDDEN:
                    callError(ExceptionCode.PERMISSION_ERROR, "权限错误~");
                    break;
                //均视为网络错误
                case NOT_FOUND:
                case REQUEST_TIMEOUT:
                case GATEWAY_TIMEOUT:
                case INTERNAL_SERVER_ERROR:
                case BAD_GATEWAY:
                case SERVICE_UNAVAILABLE:
                default:
                    callError(ExceptionCode.HTTP_EXCEPTION, "网络错误,请检查网络后再试~");
                    break;
            }
        } else if (e instanceof JsonParseException || e instanceof JSONException || e instanceof ParseException) {
            //均视为解析错误
            callError(ExceptionCode.PARSE_ERROR, "数据解析异常~");
        } else if (e instanceof SocketTimeoutException) {
            callError(ExceptionCode.SOCKET_TIMEOUT_EXCEPTION, "网络请求超时~");
        } else if (e instanceof ServerResultException) {
            ServerResultException apiException = (ServerResultException) e;
            callError(apiException.getCode(), apiException.getMessage());
        } else if (e instanceof ConnectException) {
            callError(ExceptionCode.CONNECT_EXCEPTION, "连接服务器失败~");
        } else {
            callError(ExceptionCode.UNKNOWN_ERROR, "服务器正在开小灶,请稍后再试~");
        }

        RxRetrogitLog.e(e.getMessage());

    }

    @Override
    public void onSubscribe(Disposable d) {
        Log.d(TAG, "onSubscribe");
    }

    @Override
    public void onCancel() {
        Log.d(TAG, "onCancel");
    }

    @Override
    public void onComplete() {
        Log.d(TAG, "onCompleted");
    }

    private void callError(int code, String message) {
        if (showError) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
        if (httpCallback != null) {
            httpCallback.onError(code, message);
        }
    }

    // 将onNext方法中的返回结果交给Activity或Fragment自己处理
    @Override
    public void onNext(T t) {
        if (httpCallback == null) {
            return;
        }
        httpCallback.onNext(t);
    }

}
```

整个框架的使用就这几个最low的步骤，这也仅仅是最low的使用方式，大家也可以参考我提供的demo的使用方式加以封装，毕竟框架封装的好坏是取决于调用是否简单，在傻瓜式代码的路上，草民还在努力，也想和大家一同成长~

## 项目地址

gradle  ：

```
 compile 'com.walid:rxretrofit:0.3.8'
```

# 使用说明

## 1. 注册 HttpManager（建议在 Application 中注册）

```
        ArrayList<Interceptor> interceptors = new ArrayList<>();
        interceptors.add(new ParamsInterceptor());
        RetrofitParams params = new RetrofitParams();
        // data 转换器
        GsonBuilder builder = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss");
        params.setConverterFactory(GsonConverterFactory.create(builder.create()));
        params.setConnectTimeoutSeconds(10);
        params.setReadTimeoutSeconds(10);
        params.setWriteTimeoutSeconds(10);
        params.setInterceptors(interceptors);
        params.setDebug(true);
        httpManager = HttpManagerBuilder.create()
                .setBaseUrl(ApiConstants.URL)
                .setCodeVerify(new CodeVerify())
                .setParams(params)
                .build();
```

## 2. 配置Code校验类与HttpResult 

配置规则，详见代码示例~

## 3. 发起网络请求

```
        httpManager.toSubscribe(httpManager.getApiService(IInsApi.class).list("ANDROID"), App.instalce, new SimpleHttpCallback<List<InsuranceVo>>() {
            @Override
            public void onNext(List<InsuranceVo> insuranceVos) {
                tvContent.setText("Datas = \n" + insuranceVos.toString());
            }
        }, false);
```

