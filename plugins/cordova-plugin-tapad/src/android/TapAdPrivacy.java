package com.example.tapad;

import com.tapsdk.tapad.*;

/**
 * 隐私控制器（SDK 5.3.0.4 共 11 个布尔开关，这里全部显式给出）。
 * 小游戏不需要定位、设备状态、应用列表，因此关闭；其余按官方默认保持开启以利于填充。
 */
public class TapAdPrivacy implements TapAdCustomController {
    @Override public boolean isCanUseLocation() { return false; }        // 小游戏不用定位
    @Override public TapAdLocation getTapAdLocation() { return null; }
    @Override public boolean isCanUsePhoneState() { return false; }      // 不读设备标识
    @Override public String getDevImei() { return null; }
    @Override public boolean isCanUseWifiState() { return true; }
    @Override public boolean isCanUseWriteExternal() { return true; }    // 下载类广告需要
    @Override public boolean alist() { return false; }                   // 不读应用列表
    @Override public boolean isCanUseAndroidId() { return true; }
    @Override public boolean isCanUseOaid() { return true; }             // 5.3.0.4 新增，默认开
    @Override public String getDevOaid() { return null; }                // SDK 自动获取
    // 以下为 5.3.0.0 新增开关，按官方默认
    @Override public boolean isCanUsePhoneMacAddress() { return true; }
    @Override public boolean isCanUseBssid() { return true; }
    @Override public boolean isCanUseSsid() { return true; }
    @Override public boolean isCanUseSensor() { return true; }
}
