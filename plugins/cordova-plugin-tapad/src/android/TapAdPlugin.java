package com.example.tapad;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.tapsdk.tapad.*;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;

/**
 * TapADN 桥接：init / preload / isReady / show / splash / destroy。
 * 所有失败都以回调形式返回给 JS，由前端走“无广告”路径，绝不抛出到游戏主流程。
 */
public class TapAdPlugin extends CordovaPlugin {
    private static final String TAG = "TapAd";

    private boolean inited = false;
    private TapAdNative loader;
    private TapRewardVideoAd rewardAd;
    private boolean rewardLoading = false;
    private TapInterstitialAd interAd;
    private boolean interLoading = false;
    private TapSplashAd splashAd;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public boolean execute(String action, JSONArray a, CallbackContext cb) throws JSONException {
        switch (action) {
            case "init":    init(a.getString(0), a.getString(1), a.getString(2), a.optBoolean(3, false), cb); return true;
            case "isReady": cb.success(isReady(a.getString(0)) ? 1 : 0); return true;
            case "preload": preload(a.getString(0), a.getString(1), a.optString(2, ""), a.optString(3, "revive"), a.optString(4, "1"), cb); return true;
            case "show":    show(a.getString(0), cb); return true;
            case "splash":  splash(a.getString(0), a.optInt(1, 3000), cb); return true;
            case "destroy": destroy(); cb.success(); return true;
            default: return false;
        }
    }

    private void ui(Runnable r) { cordova.getActivity().runOnUiThread(r); }

    /** 流式事件：keep=true 表示后面还有事件 */
    private void emit(CallbackContext cb, String ev, boolean keep, Object... kv) {
        try {
            JSONObject o = new JSONObject().put("e", ev);
            for (int i = 0; i + 1 < kv.length; i += 2) o.put(String.valueOf(kv[i]), kv[i + 1]);
            PluginResult r = new PluginResult(PluginResult.Status.OK, o);
            r.setKeepCallback(keep);
            cb.sendPluginResult(r);
        } catch (Throwable t) { Log.e(TAG, "emit fail", t); }
    }

    /**
     * 用反射调 Builder 的 withMediaId / withSpaceId：
     * 官方文档里这两个参数的类型示例不一致（long / int / String），按方法实际签名自动转换，避免编译期猜错。
     */
    private static void callIdSetter(Object builder, String name, String value) throws Exception {
        for (Method m : builder.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterTypes().length != 1) continue;
            Class<?> t = m.getParameterTypes()[0];
            Object v;
            if (t == long.class || t == Long.class) v = Long.parseLong(value);
            else if (t == int.class || t == Integer.class) v = Integer.parseInt(value);
            else v = value;
            m.invoke(builder, v);
            return;
        }
        throw new NoSuchMethodException(name);
    }

    /**
     * 隐私控制器（SDK 5.3.0.4 的 11 个布尔开关全部显式给出）。
     * 小游戏不需要定位、设备状态、应用列表，因此关闭；其余保持官方默认。
     * 用匿名类写法与官方示例一致；getTapAdLocation / getDevImei / getDevOaid 保持默认 null，不重写。
     */
    private TapAdCustomController privacy() {
        return new TapAdCustomController() {
            @Override public boolean isCanUseLocation() { return false; }
            @Override public boolean isCanUsePhoneState() { return false; }
            @Override public boolean isCanUseWifiState() { return true; }
            @Override public boolean isCanUseWriteExternal() { return true; }
            @Override public boolean alist() { return false; }
            @Override public boolean isCanUseAndroidId() { return true; }
            @Override public boolean isCanUseOaid() { return true; }
            // 5.3.0.0 新增开关
            @Override public boolean isCanUsePhoneMacAddress() { return true; }
            @Override public boolean isCanUseBssid() { return true; }
            @Override public boolean isCanUseSsid() { return true; }
            @Override public boolean isCanUseSensor() { return true; }
        };
    }

    private static int toInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return 1; }
    }

    /**
     * 激励视频交互监听：SDK 5.3.0.4 实际的回调签名与文档不同（回调带 TapRewardVideoAd 参数），
     * 因此用动态代理实现接口，按方法名分发，不依赖具体参数个数。
     * 是否发奖的唯一依据：onRewardVerify 的布尔参数为 true。
     */
    private TapRewardVideoAd.RewardAdInteractionListener rewardListener(final CallbackContext cb) {
        return (TapRewardVideoAd.RewardAdInteractionListener) java.lang.reflect.Proxy.newProxyInstance(
                TapRewardVideoAd.RewardAdInteractionListener.class.getClassLoader(),
                new Class<?>[]{TapRewardVideoAd.RewardAdInteractionListener.class},
                (proxy, method, args) -> {
                    String n = method.getName();
                    if ("hashCode".equals(n)) return System.identityHashCode(proxy);
                    if ("equals".equals(n)) return args != null && args.length > 0 && proxy == args[0];
                    if ("toString".equals(n)) return "TapAdRewardListener";
                    switch (n) {
                        case "onAdShow": emit(cb, "show", true); break;
                        case "onAdClose": emit(cb, "close", false); break;
                        case "onVideoComplete": emit(cb, "complete", true); break;
                        case "onVideoError": emit(cb, "error", false, "msg", "video_error"); break;
                        case "onRewardVerify": {
                            boolean ok = false;
                            if (args != null) for (Object o : args) { if (o instanceof Boolean) { ok = (Boolean) o; break; } }
                            emit(cb, "verify", true, "ok", ok);
                            break;
                        }
                        case "onSkippedVideo": emit(cb, "skip", true); break;
                        case "onAdClick": emit(cb, "click", true); break;
                        default: break;   // onAdValidShow 等不需要处理
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) return false;
                    if (rt == int.class) return 0;
                    return null;
                });
    }

    private TapAdNative loader() {
        if (loader == null) loader = TapAdManager.get().createAdNative(cordova.getActivity());
        return loader;
    }

    // ---------- 初始化（必须在用户同意隐私协议之后由 JS 调用） ----------
    private void init(final String mediaId, final String mediaName, final String mediaKey, final boolean debug, final CallbackContext cb) {
        ui(() -> {
            try {
                if (inited) { cb.success(); return; }
                TapAdConfig.Builder b = new TapAdConfig.Builder();
                callIdSetter(b, "withMediaId", mediaId);      // TODO(MEDIA) 在 JS 配置里替换
                b.withMediaName(mediaName)                    // TODO(MEDIA)
                 .withMediaKey(mediaKey)                      // TODO(MEDIA)
                 .enableDebug(debug)
                 .shakeEnabled(true)
                 .withCustomController(privacy());
                TapAdSdk.init(cordova.getActivity().getApplication(), b.build());
                inited = true;
                cb.success();
            } catch (Throwable t) {
                Log.e(TAG, "init fail", t);
                cb.error(String.valueOf(t));
            }
        });
    }

    private boolean isReady(String kind) {
        return "reward".equals(kind) ? rewardAd != null : interAd != null;
    }

    // ---------- 预加载：加载中不重复请求 ----------
    private void preload(final String kind, final String spaceId, final String userId,
                         final String rewardName, final String rewardAmount, final CallbackContext cb) {
        ui(() -> {
            if (!inited) { cb.error("not_init"); return; }
            try {
                AdRequest.Builder b = new AdRequest.Builder();
                callIdSetter(b, "withSpaceId", spaceId);      // TODO(SPACE_ID)
                if ("reward".equals(kind)) {
                    if (rewardAd != null || rewardLoading) { cb.success("busy"); return; }
                    rewardLoading = true;
                    b.withRewardName(rewardName).withRewardAmount(toInt(rewardAmount)).withUserId(userId);
                    loader().loadRewardVideoAd(b.build(), new TapAdNative.RewardVideoAdListener() {
                        @Override public void onError(int code, String message) {
                            Log.e(TAG, "reward code=" + code + ", msg=" + message);
                            rewardLoading = false; cb.error(code + ":" + message);
                        }
                        @Override public void onRewardVideoAdLoad(TapRewardVideoAd ad) { /* 等素材缓存好再展示 */ }
                        @Override public void onRewardVideoCached(TapRewardVideoAd ad) {
                            rewardAd = ad; rewardLoading = false; cb.success("ok");
                        }
                    });
                } else {
                    if (interAd != null || interLoading) { cb.success("busy"); return; }
                    interLoading = true;
                    loader().loadInterstitialAd(b.build(), new TapAdNative.InterstitialAdListener() {
                        @Override public void onInterstitialAdLoad(TapInterstitialAd ad) {
                            interAd = ad; interLoading = false; cb.success("ok");
                        }
                        @Override public void onError(int code, String message) {
                            Log.e(TAG, "inter code=" + code + ", msg=" + message);
                            interLoading = false; cb.error(code + ":" + message);
                        }
                    });
                }
            } catch (Throwable t) {
                Log.e(TAG, "preload fail", t);
                if ("reward".equals(kind)) rewardLoading = false; else interLoading = false;
                cb.error(String.valueOf(t));
            }
        });
    }

    // ---------- 展示：事件流 show / click / verify / complete / skip / error / close ----------
    private void show(final String kind, final CallbackContext cb) {
        ui(() -> {
            try {
                if ("reward".equals(kind)) {
                    final TapRewardVideoAd ad = rewardAd;
                    if (ad == null) { emit(cb, "error", false, "msg", "not_ready"); return; }
                    rewardAd = null;                                   // 一次性，展示后立刻置空
                    ad.setRewardAdInteractionListener(rewardListener(cb));
                    ad.showRewardVideoAd(cordova.getActivity());
                } else {
                    final TapInterstitialAd ad = interAd;
                    if (ad == null) { emit(cb, "error", false, "msg", "not_ready"); return; }
                    interAd = null;
                    ad.setInteractionListener(new TapInterstitialAd.InterstitialAdInteractionListener() {
                        @Override public void onAdShow() { emit(cb, "show", true); }
                        @Override public void onAdClose() { emit(cb, "close", false); }
                        @Override public void onAdError() { emit(cb, "error", false, "msg", "ad_error"); }
                        @Override public void onAdValidShow() { }
                        @Override public void onAdClick() { emit(cb, "click", true); }
                    });
                    ad.show(cordova.getActivity());
                }
            } catch (Throwable t) {
                Log.e(TAG, "show fail", t);
                emit(cb, "error", false, "msg", String.valueOf(t));
            }
        });
    }

    // ---------- 开屏：超时 / 失败 / 过期一律返回 done，由前端直接进游戏 ----------
    private void splash(final String spaceId, final int timeoutMs, final CallbackContext cb) {
        ui(() -> {
            if (!inited) { emit(cb, "done", false, "why", "not_init"); return; }
            final boolean[] finished = {false};
            final Runnable finish = () -> {
                if (finished[0]) return;
                finished[0] = true;
                try { if (splashAd != null) { splashAd.destroyView(); splashAd.dispose(); } } catch (Throwable ignore) { }
                splashAd = null;
                emit(cb, "done", false);
            };
            main.postDelayed(finish, timeoutMs);                      // 超时直接进游戏，不卡用户
            try {
                AdRequest.Builder b = new AdRequest.Builder();
                callIdSetter(b, "withSpaceId", spaceId);              // TODO(SPACE_ID)
                loader().loadSplashAd(b.build(), new TapAdNative.SplashAdListener() {
                    @Override public void onSplashAdLoad(TapSplashAd ad) {
                        if (finished[0]) { ad.dispose(); return; }
                        splashAd = ad;
                        if (!ad.isValid()) { finish.run(); return; }  // 先判有效再展示
                        ad.setSplashInteractionListener(new TapSplashAd.AdInteractionListener() {
                            @Override public void onAdSkip() { finish.run(); }
                            @Override public void onAdTimeOver() { finish.run(); }
                            @Override public void onAdClick() { }
                            @Override public void onAdShow() { }
                            @Override public void onAdValidShow() { }
                        });
                        ad.show(cordova.getActivity());
                    }
                    @Override public void onError(int code, String message) {
                        Log.e(TAG, "splash code=" + code + ", msg=" + message);
                        finish.run();
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "splash fail", t);
                finish.run();
            }
        });
    }

    private void destroy() {
        try { if (splashAd != null) { splashAd.destroyView(); splashAd.dispose(); } } catch (Throwable ignore) { }
        splashAd = null;
        try { if (loader != null) loader.dispose(); } catch (Throwable ignore) { }
        loader = null; rewardAd = null; interAd = null;
        rewardLoading = false; interLoading = false;
    }

    @Override
    public void onDestroy() {
        destroy();
        super.onDestroy();
    }
}
