# Estimote Android Cordova plugin

This plugin is forked from [Evothings phonegap-estimotebeacons](https://github.com/evothings/phonegap-estimotebeacons) and heavily modified. Original documentation from Evothings can be found [here](https://github.com/evothings/phonegap-estimotebeacons/blob/master/documentation.md).

All changes made in this fork pertain to the Estimote Android SDK ([https://github.com/Estimote/Android-SDK](current: 0.11.0)). All iOS functionality has been removed (may be added back later).


# Notes

If the Estimote Android SDK does not compile, first try to clean and rebuild. If that does not work, you may need to manually add the dependency to your build.gradle file:

```
dependencies {
    compile 'com.estimote:sdk:0.11.0@aar';
```
