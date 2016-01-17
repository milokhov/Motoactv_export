# Motoactv_export
Motoactv TCX exporter

MOTOACTV_export is a custom service for the MOTOACTV that will export the workout data table to a TCX file on the SD Card. The TCX file will be automatically created every time a workout ends and will be named with the unique id of the workout. The app can also auto upload to runkeepr, google drive, dropbox and post to facebook through runkeepr.

This app is a work in progress, try at your own risk.

**ADB Commands**

**Install**

adb install Motoactv_export.apk

**Uninstall**

adb uninstall com.sdsoft.motoactv_export
adb uninstall com.sdsoft.sd_csv (if coming from old version)

**Start Motoactv_export without having a launcher**

adb shell am start -n com.sdsoft.motoactv_export/com.sdsoft.motoactv_export.MainActivity

**Optional**

A modified settings.apk that has an Export TCX option that will launch the manual TCX exporter app. Only use if your on stock roms or your settings.apk has not already been modified. 

To install 

adb remount

adb push Settings.apk /system/app

adb reboot
