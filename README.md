[<img src="https://assets-global.website-files.com/6050a76fa6a633d5d54ae714/62c452fdba7add3d406dc750_ads-sdk__no-nav-p-1080.jpeg" height="400"></img>](https://www.mapbox.com/adas-sdk)

# ADAS SDK

The ADAS SDK allows you to build Advanced Driver Assistance Systems (ADAS) functions with daily updated map data from Mapbox.

The ADAS SDK provides on-board map-matching, predictive tile caching and an electronic horizon module. The electronic horizon module predicts the road ahead for up to 10km and supports two flavors of data output:
1. Advanced Driver Assistance Systems Interface Specification ([ADASIS](https://adasis.org/)) v2 binary format for direct Controller Area Network (CAN) bus integration.
2. Mapbox object-oriented format. [Learn more about Mapbox Electronic Horizon module](https://docs.mapbox.com/android/navigation/guides/advanced/electronic-horizon/).


> **The Mapbox ADAS SDK is in public beta** and is subject to changes, including its pricing. Use of the SDK is subject to the beta product restrictions in the [Mapbox Terms of Service](https://www.mapbox.com/legal/tos). Mapbox reserves the right to end any free tier or free evaluation offers at any time and require customers to place an order to purchase the Mapbox ADAS SDK, regardless of the level of use.

# ADAS SDK Evaluation Kit for Android
<img src=".github/sample.gif" align="right" height="500"></img>
The ADAS SDK is written in pure C++ and supports Android, and Linux or other platforms [by request](https://www.mapbox.com/contact/sales/?utm_source=github&utm_medium=readme&utm_content=adas-sdk). We bundled the ADAS SDK with the Mapbox Navigation SDK for Android to facilitate faster evaluation, research and development of ADAS features. The Navigation SDK provides a collection of features that are critical when building navigation projects, including navigation routes, accurate device location updates, and dynamic camera adjustments during turn-by-turn navigation. Evaluating the ADAS SDK requires only a couple of additional lines of code in a Mapbox Navigation SDK powered project.

In a [sample](/samples/android/adas-sdk-example) folder you would find a template application for Android which:
- Uses latest Mapbox data
- Builds routes using [Navigation | API | Mapbox](https://docs.mapbox.com/api/navigation/#directions)
- Simulates test drive on a route
- Saves ADASISv2 messages to .json file for further analysis
- Saves route to a .json file (please view [Directions | API | Mapbox](https://docs.mapbox.com/api/navigation/directions/#retrieve-directions) for a detailed _route.json_ specification)

## Getting started with ADASIS

### Install and initialize the NavSDK

[First, follow the installtion guide for Mapbox Navigation SDK](https://docs.mapbox.com/android/navigation/guides/get-started/install/)[, and configure with your Mapbox account credentials.](https://docs.mapbox.com/android/navigation/guides/get-started/install/)

[Initialize the Navigation SDK](https://docs.mapbox.com/android/navigation/guides/get-started/initialization/) and start a trip session to begin consuming and processing data that can power either passive navigation (free-drive mode) or active guidance (turn-by-turn navigation).

### The ADASIS module initialization and configuration

ADASISv2 capabilities are available via `mapboxNavigation.experimental `facade. Any navigation app based on Mapbox Navigation SDK version 2.6.1 or greater can be utilized to generate ADASISv2 messages.

The ADASIS module is activated by setting an observer implementing `ADASISv2MessageCallback` interface. The method run of `ADASISv2MessageCallback` is called asynchronously at intervals according to ADASISv2 specifications and configuration provided. There can only be one ADASIS observer registered at a time.

```Kotlin
mapboxNavigation.experimental.setAdasisMessageCallback(adasisObserver, AdasisConfigBuilder.defaultOptions())
    
private val adasisObserver = object : ADASISv2MessageCallback {...}

public interface ADASISv2MessageCallback {
    void run(@NonNull ADASISv2Message message);
}
```
The ADASIS module configuration of type AdasisConfig is provided as a second param to a setAdasisMessageCallback function call and can be prepared using `AdasisConfigBuilder` and its static methods `defaultOptions()` or `fromJson()`. Using default options is a recommended approach however `AdasisConfig` can be intialized explicitly with `AdasisConfigCycleTimes`, `AdasisConfigDataSending` and `AdasisConfigPathsConfigs`.

<details><summary>A list of supported options in a json format can be found below</summary>

```json
{
    "cycleTimes": {
        "metadataCycleOnStartMs": 100,
        "metadataCycleSeconds": 5,
        "positionCycleMs": 200
    },
    "dataSending": {
        "messageIntervalMs": 80,
        "messagesInPackage": 4,
        "sortProfileshortsByOffset": true,
        "sortProfilelongsByOffset": true,
        "enableRetransmission": true
    },
    "pathsConfigs": {
        "mpp": {
            "stub": {
                "enable": true,
                "radiusMeters": 2000,
                "repetitionMeters": 300
            },
            "segment": {
                "enable": true,
                "radiusMeters": 2000,
                "repetitionMeters": 300
            },
            "profileshort": {
                "enable": true,
                "radiusMeters": 2000,
                "repetitionMeters": 300,
                "types": {
                    "slopeStep": false,
                    "slopeLinear": true,
                    "curvature": true,
                    "routeNumTypes": false,
                    "roadCondition": true,
                    "roadAccessibility": true,
                    "variableSpeedSign": false,
                    "headingChange": true
                }
            },
            "profilelong": {
                "enable": true,
                "radiusMeters": 2000,
                "repetitionMeters": 300,
                "types": {
                    "lat": true,
                    "lon": true,
                    "alt": true,
                    "trafficSign": false,
                    "extendedLane": false
                }
            }
        },
        "level1": {
            "stub": {
                "enable": true,
                "radiusMeters": 300,
                "repetitionMeters": 0
            },
            "segment": {
                "enable": true,
                "radiusMeters": 500,
                "repetitionMeters": 100
            },
            "profileshort": {
                "enable": true,
                "radiusMeters": 500,
                "repetitionMeters": 200,
                "types": {
                    "slopeStep": false,
                    "slopeLinear": true,
                    "curvature": true,
                    "routeNumTypes": false,
                    "roadCondition": true,
                    "roadAccessibility": true,
                    "variableSpeedSign": false,
                    "headingChange": true
                }
            },
            "profilelong": {
                "enable": true,
                "radiusMeters": 300,
                "repetitionMeters": 30,
                "types": {
                    "lat": true,
                    "lon": true,
                    "alt": true,
                    "trafficSign": false,
                    "extendedLane": false
                }
            }
        },
        "level2": {
            "stub": {
                "enable": true,
                "radiusMeters": 200,
                "repetitionMeters": 0
            },
            "segment": {
                "enable": true,
                "radiusMeters": 300,
                "repetitionMeters": 100
            },
            "profileshort": {
                "enable": true,
                "radiusMeters": 300,
                "repetitionMeters": 200,
                "types": {
                    "slopeStep": false,
                    "slopeLinear": true,
                    "curvature": true,
                    "routeNumTypes": false,
                    "roadCondition": true,
                    "roadAccessibility": true,
                    "variableSpeedSign": false,
                    "headingChange": true
                }
            },
            "profilelong": {
                "enable": false,
                "radiusMeters": 200,
                "repetitionMeters": 30,
                "types": {
                    "lat": true,
                    "lon": true,
                    "alt": true,
                    "trafficSign": false,
                    "extendedLane": false
                }
            }
        }
    }
}
```
</details>

Use the `resetAdasisMessageCallback() `method to remove the observer and disable the ADASIS module:

```Kotlin
mapboxNavigation.experimental.resetAdasisMessageCallback()
```

### ADASISv2 Messages format

ADASISv2 is a binary format and suitable for a [CAN bus](https://en.wikipedia.org/wiki/CAN_bus). Several helper methods can return binary form in different representation:

- `toBigEndian()` and `toLittleEndian()` for binary
- `toHex() `for a hex string
- `toJson()` for Json.

Below is an example of mixed hex and json representation of a binary ADASISv2 message in its more readable json representation:

```json
[
 "20528835fe2e59fd",
 {
   "type": "POSITION",
   "pathIndex": 8,
   "offset": 82,
   "cyclicCounter": 2,
   "posIndex": 0,
   "positionAge": 510,
   "speed": 89,
   "relativeHeading": 253,
   "positionProbability": 26,
   "positionConfidence": 2,
   "currentLane": 7
 }
]
```

## Getting started with Mapbox object-oriented format
To use an object-oriented electronic horizon format please use the following [documentation](https://docs.mapbox.com/android/navigation/guides/advanced/electronic-horizon/) of a Navigation SDK. [Direct calls to the electronic horizon](https://docs.mapbox.com/android/navigation/guides/advanced/electronic-horizon/#direct-calls-to-the-electronic-horizon) section explains how to obtain metadata for any edge belonging to an MPP. To get extended ADAS attributes use `getAdasAttributes(long edgeId)` method of an experimental facade. Returned `EdgeAdasAttributes` object provides a set of getters for speed limits, slopes and curvatures:

```Kotlin
public List<SpeedLimitInfo> getSpeedLimit()
public List<ValueOnEdge> getSlopes()
public List<ValueOnEdge> getCurvatures()
```

# Data limitations
Extended ADAS attributes (road curvature and road slope) are only available in limited geography: the greater Munich area. Basic road information (geometry, road class and speed limits, etc) for SEGMENT, POSITION, STUB and certain PROFILE messages are available globally.

Data samples containing slope and curvature information are available per customer request. If you want to evaluate Mapbox ADAS data further, please [contact sales](https://www.mapbox.com/contact/sales/?utm_source=github&utm_medium=readme&utm_content=adas-sdk).

# Pricing
The ADAS SDK Evaluation Kit is supplied in the Mapbox Navigation SDK, so Mapbox Navigation SDK pricing applies. Up to 1000 trips per month are free, which allows for a good number of tests free of charge. You can learn about Navigation SDK pricing [here](https://docs.mapbox.com/android/navigation/guides/pricing/) and [here](https://www.mapbox.com/pricing#navigation). For ADAS SDK specific pricing outside of evaluation, please reach out to our [sales team](https://www.mapbox.com/contact/sales/?utm_source=github&utm_medium=readme&utm_content=adas-sdk).

# Contact us
For technical queries [contact auto team](https://www.mapbox.com/contact/auto).
