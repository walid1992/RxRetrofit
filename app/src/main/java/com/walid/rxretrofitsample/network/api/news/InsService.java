package com.walid.rxretrofitsample.network.api.news;

import com.walid.rxretrofit.HttpManager;
import com.walid.rxretrofit.interfaces.IHttpCallback;
import com.walid.rxretrofitsample.network.SeaApiUtils;

/**
 * Author   : walid
 * Data     : 2016-08-29  14:47
 * Describe :
 */

public class InsService {

    private static final IInsApi INS_API = HttpManager.getInstance().getApiService(IInsApi.class);

    public static void latest(final IHttpCallback<Object> listener) {
        SeaApiUtils.toSubscribe(INS_API.list("ANDROID"), listener);
    }

}