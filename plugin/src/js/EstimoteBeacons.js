var _estimote = function() {
    if (window.Estimote) return window.Estimote;

    var HelperUtils = {
		// isIos: function() { /** iOS only**/
		// 	return ((navigator.userAgent.match(/iPad/i))  == "iPad") || ("iPad" : (navigator.userAgent.match(/iPhone/i))  == "iPhone"));
		// },
        noop: function() {},
        // helper_createTriggerObject: function(trigger) { /** iOS only**/
        //     var triggerObject = {},
        //         triggerObject.triggerIdentifier = trigger.identifier,
        //         triggerObject.rules = [];
        //     for (var i = 0; i < trigger.rules.length; ++i) {
        //         var rule = trigger.rules[i];
        //         triggerObject.rules.push({
        //             ruleType: rule.ruleType,
        //             ruleIdentifier: rule.ruleIdentifier,
        //             nearableIdentifier: rule.nearableIdentifier,
        //             nearableType: rule.nearableType
        //         });
        //     }
		//
        //     return triggerObject;
        // },
        // helper_updateTriggerRule: function(trigger, event) { /** iOS only**/
        //     var rule = trigger.ruleTable[event.ruleIdentifier];
        //     if (rule && rule.ruleUpdateFunction) {
        //         rule.ruleUpdateFunction(rule, event.nearable, event);
        //         HelperUtils.helper_updateRuleState(
        //             event.triggerIdentifier,
        //             event.ruleIdentifier,
        //             rule.state);
        //     }
        // },
        // helper_updateRuleState: function(triggerIdentifier, ruleIdentifier, state) { /** iOS only**/
        //     cordova.exec(null,
        //         null,
        //         'EstimoteBeacons',
        //         'triggers_updateRuleState', [triggerIdentifier, ruleIdentifier, state]
        //     );
        // },
        checkType: function(name, obj, type, caller) {
            if (typeof(obj) != type) {
                console.error('Error: ' + (name || '') + ' parameter is not an object in: ' + (caller || ''));
                return false;
            }
            return true;
        },
        processParams: function(obj) {
            obj = obj || {};
            obj.data = obj.data || [];
			if (!(obj.data instanceof Array)) obj.data = [obj.data];
            obj.success = obj.success || HelperUtils.noop;
            obj.error = obj.error || HelperUtils.noop;
        }
    };

    HelperUtils.cordova = {
        exec: function(obj, method) {
            return cordova.exec(obj.success, obj.error, 'EstimoteBeacons', method, obj.data);
        },
        execParamsRegionSuccessError: function(obj) {
            obj = obj || {};
            var caller = HelperUtils.cordova.execParamsRegionSuccessError.caller.name;

            return (HelperUtils.checkType('data', obj.data, 'object', caller) &&
                HelperUtils.checkType('success', obj.success, 'function', caller) &&
                HelperUtils.checkType('error', obj.error, 'function', caller));
        },
        execParamsSuccessError: function(obj) {
            obj = obj || {};
            var caller = HelperUtils.cordova.execParamsSuccessError.caller.name;

            return (HelperUtils.checkType('success', obj.success, 'function', caller) &&
                HelperUtils.checkType('error', obj.error, 'function', caller));
        },
        execParamsRegion: function(obj) {
            obj = obj || {};
            var caller = HelperUtils.cordova.execParamsRegion.caller.name;

            return (HelperUtils.checkType('data', obj.data, 'object', caller));
        }
    };


    this.bluetoothState = function(obj) {
        obj = HelperUtils.processParams(obj);
        HelperUtils.cordova.exec(obj, 'bluetooth_bluetoothState');

        return true;
    };

    this.toggleVerbose = function(obj) {
        obj = HelperUtils.processParams(obj);
        HelperUtils.cordova.exec(obj, 'toggleVerbose');

        return true;
    };

    this.beacons = {
        ProximityUnknown: 0,
        ProximityImmediate: 1,
        ProximityNear: 2,
        ProximityFar: 3,

        BeaconColorUnknown: 0,
        BeaconColorMintCocktail: 1,
        BeaconColorIcyMarshmallow: 2,
        BeaconColorBlueberryPie: 3,
        BeaconColorSweetBeetroot: 4,
        BeaconColorCandyFloss: 5,
        BeaconColorLemonTart: 6,
        BeaconColorVanillaJello: 7,
        BeaconColorLiquoriceSwirl: 8,
        BeaconColorWhite: 9,
        BeaconColorTransparent: 10,

        RegionStateUnknown: 'unknown',
        RegionStateOutside: 'outside',
        RegionStateInside: 'inside',

        // requestWhenInUseAuthorization: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     HelperUtils.cordova.exec(obj, 'beacons_requestWhenInUseAuthorization');
		//
        //     return true;
        // },
        // requestAlwaysAuthorization: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     HelperUtils.cordova.exec(obj, 'beacons_requestAlwaysAuthorization');
		//
        //     return true;
        // },
        // authorizationStatus: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     if (!HelperUtils.cordova.execParamsSuccessError(obj)) return false;
		//
        //     HelperUtils.cordova.exec(obj, 'beacons_authorizationStatus', obj.data);
		//
        //     return true;
        // },
        // startAdvertisingAsBeacon: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     HelperUtils.cordova.exec(obj, 'beacons_startAdvertisingAsBeacon'); // [uuid, major, minor, regionId]
		//
        //     return true;
        // },
        // stopAdvertisingAsBeacon: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     HelperUtils.cordova.exec(obj, 'beacons_stopAdvertisingAsBeacon');
		//
        //     return true;
        // },
        enableAnalytics: function(obj) {
            obj = HelperUtils.processParams(obj);
            HelperUtils.cordova.exec(obj, 'beacons_enableAnalytics');

            return true;
        },
        isAnalyticsEnabled: function(obj) {
            obj = HelperUtils.processParams(obj);
            HelperUtils.cordova.exec(obj, 'beacons_isAnalyticsEnabled');

            return true;
        },
        // isAuthorized: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     HelperUtils.cordova.exec(obj, 'beacons_isAuthorized');
		//
        //     return true;
        // },
        setupAppIDAndAppToken: function(obj) {
            obj = HelperUtils.processParams(obj);
            HelperUtils.cordova.exec(obj, 'beacons_setupAppIDAndAppToken'); // [appID, appToken]

            return true;
        },
        // startEstimoteBeaconDiscovery: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     HelperUtils.cordova.exec(obj, 'beacons_startEstimoteBeaconDiscovery');
		//
        //     return true;
        // },
        // stopEstimoteBeaconDiscovery: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     HelperUtils.cordova.exec(obj, 'beacons_stopEstimoteBeaconDiscovery');
		//
        //     return true;
        // },
        startScanningDevices: function(obj) {
            obj = HelperUtils.processParams(obj);
            HelperUtils.cordova.exec(obj, 'beacons_startScanningDevices'); // [settings]

            return true;
        },
        stopScanningDevices: function(obj) {
            obj = HelperUtils.processParams(obj);
            HelperUtils.cordova.exec(obj, 'beacons_stopScanningDevices');

            return true;
        },
        startRangingBeaconsInRegion: function(obj) {
            obj = HelperUtils.processParams(obj);
            if (!HelperUtils.cordova.execParamsRegionSuccessError(obj)) {
                return false;
            }

            HelperUtils.cordova.exec(obj, 'beacons_startRangingBeaconsInRegion'); // [region]

            return true;
        },
        stopRangingBeaconsInRegion: function(obj) {
            obj = HelperUtils.processParams(obj);
            if (!HelperUtils.cordova.execParamsRegion(obj)) {
                return false;
            }

            HelperUtils.cordova.exec(obj, 'beacons_stopRangingBeaconsInRegion'); // [region]

            return true;
        },
        // startRangingSecureBeaconsInRegion: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     if (!HelperUtils.cordova.execParamsRegionSuccessError(obj)) {
        //         return false;
        //     }
		//
        //     HelperUtils.cordova.exec(obj, 'beacons_startRangingSecureBeaconsInRegion'); // [region]
		//
        //     return true;
        // },
        // stopRangingSecureBeaconsInRegion: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     if (!HelperUtils.cordova.execParamsRegion(obj)) {
        //         return false;
        //     }
		//
        //     HelperUtils.cordova.exec(obj, 'beacons_stopRangingSecureBeaconsInRegion'); // [region]
		//
        //     return true;
        // },
        startMonitoringForRegion: function(obj) {
            obj = HelperUtils.processParams(obj);
            if (!HelperUtils.cordova.execParamsRegionSuccessError(obj)) {
                return false;
            }
            if (obj.data.length == 1) obj.data.push(true); // notifyEntryStateOnDisplay

            HelperUtils.cordova.exec(obj, 'beacons_startMonitoringForRegion'); // [region, !!notifyEntryStateOnDisplay]

            return true;
        },
        stopMonitoringForRegion: function(obj) {
            obj = HelperUtils.processParams(obj);
            if (!HelperUtils.cordova.execParamsRegion(obj)) {
                return false;
            }

            HelperUtils.cordova.exec(obj, 'beacons_stopMonitoringForRegion'); // [region]

            return true;
        },
        // startSecureMonitoringForRegion: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     if (!HelperUtils.cordova.execParamsRegionSuccessError(obj)) {
        //         return false;
        //     }
        //     if (obj.data.length == 1) obj.data.push(true); // notifyEntryStateOnDisplay
		//
        //     HelperUtils.cordova.exec(obj, 'beacons_startSecureMonitoringForRegion'); // [region, !!notifyEntryStateOnDisplay]
		//
        //     return true;
        // },
        // stopSecureMonitoringForRegion: function(obj) { /** iOS only**/
        //     obj = HelperUtils.processParams(obj);
        //     if (!HelperUtils.cordova.execParamsRegion(obj)) {
        //         return false;
        //     }
		//
        //     HelperUtils.cordova.exec(obj, 'beacons_stopSecureMonitoringForRegion'); // [region]
		//
        //     return true;
        // },
        connectToBeacon: function(obj) {
            obj = HelperUtils.processParams(obj);
            if (typeof(beacon) !== 'object') {
                return false;
            }

            HelperUtils.cordova.exec(obj, 'beacons_connectToBeacon'); // [beacon]

            return true;
        },
        disconnectConnectedBeacon: function(obj) {
            obj = HelperUtils.processParams(obj);
            HelperUtils.cordova.exec(obj, 'beacons_disconnectConnectedBeacon');

            return true;
        },
        writeConnectedProximityUUID: function(obj) {
            obj = HelperUtils.processParams(obj);
            HelperUtils.cordova.exec(obj, 'beacons_writeConnectedProximityUUID'); // [uuid.toLowerCase()]
        },
        writeConnectedMajor: function(obj) {
            obj = HelperUtils.processParams(obj);
            HelperUtils.cordova.exec(obj, 'beacons_writeConnectedMajor'); // [major]
        },
        writeConnectedMinor: function(obj) {
            obj = HelperUtils.processParams(obj);
            HelperUtils.cordova.exec(obj, 'beacons_writeConnectedMinor'); // [minor]
        }
    };

	/** iOS only**/
    // this.nearables = {
    //     NearableTypeUnknown: 0,
    //     NearableTypeDog: 1,
    //     NearableTypeCar: 2,
    //     NearableTypeFridge: 3,
    //     NearableTypeBag: 4,
    //     NearableTypeBike: 5,
    //     NearableTypeChair: 6,
    //     NearableTypeBed: 7,
    //     NearableTypeDoor: 8,
    //     NearableTypeShoe: 9,
    //     NearableTypeGeneric: 10,
    //     NearableTypeAll: 11,
	//
    //     NearableZoneUnknown: 0,
    //     NearableZoneImmediate: 1,
    //     NearableZoneNear: 2,
    //     NearableZoneFar: 3,
	//
    //     NearableOrientationUnknown: 0,
    //     NearableOrientationHorizontal: 1,
    //     NearableOrientationHorizontalUpsideDown: 2,
    //     NearableOrientationVertical: 3,
    //     NearableOrientationVerticalUpsideDown: 4,
    //     NearableOrientationLeftSide: 5,
    //     NearableOrientationRightSide: 6,
	//
    //     NearableColorUnknown: 0,
    //     NearableColorMintCocktail: 1,
    //     NearableColorIcyMarshmallow: 2,
    //     NearableColorBlueberryPie: 3,
    //     NearableColorSweetBeetroot: 4,
    //     NearableColorCandyFloss: 5,
    //     NearableColorLemonTart: 6,
	//
    //     startRangingForIdentifier: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         HelperUtils.cordova.exec(obj, 'nearables_startRangingForIdentifier',
    //                 obj.data // [identifier]
    //             ),
    //             return true;
    //     },
    //     stopRangingForIdentifier: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         HelperUtils.cordova.exec(obj, 'nearables_stopRangingForIdentifier',
    //                 obj.data // [identifier]
    //             ),
    //             return true;
    //     },
    //     startRangingForType: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         HelperUtils.cordova.exec(obj, 'nearables_startRangingForType',
    //                 obj.data // [type]
    //             ),
    //             return true;
    //     },
    //     stopRangingForType: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         HelperUtils.cordova.exec(obj, 'nearables_stopRangingForType',
    //                 obj.data // [type]
    //             ),
    //             return true;
    //     },
    //     stopRanging: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         HelperUtils.cordova.exec(obj, 'nearables_stopRanging',
    //                 obj.data
    //             ),
    //             return true;
    //     },
    //     startMonitoringForIdentifier: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         HelperUtils.cordova.exec(obj, 'nearables_startMonitoringForIdentifier',
    //                 obj.data // [identifier]
    //             ),
    //             return true;
    //     },
    //     stopMonitoringForIdentifier: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         HelperUtils.cordova.exec(obj, 'nearables_stopMonitoringForIdentifier',
    //                 obj.data // [identifier]
    //             ),
    //             return true;
    //     },
    //     startMonitoringForType: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         HelperUtils.cordova.exec(obj, 'nearables_startMonitoringForType',
    //                 obj.data // [type]
    //             ),
    //             return true;
    //     },
    //     stopMonitoringForType: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         HelperUtils.cordova.exec(obj, 'nearables_stopMonitoringForType',
    //                 obj.data // [type]
    //             ),
    //             return true;
    //     },
    //     stopMonitoring: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         HelperUtils.cordova.exec(obj, 'nearables_stopMonitoring',
    //                 obj.data
    //             ),
    //             return true;
    //     }
    // };
	//
    // var ruleCounter = 0;
	//
    // this.triggers = {
    //     RuleTypeGeneric: 1,
    //     RuleTypeNearableIdentifier: 2,
    //     RuleTypeNearableType: 3,
    //     RuleTypeInRangeOfNearableIdentifier: 4,
    //     RuleTypeInRangeOfNearableType: 5,
    //     RuleTypeOutsideRangeOfNearableIdentifier: 6,
    //     RuleTypeOutsideRangeOfNearableType: 7,
	//
    //     createTrigger: function(triggerIdentifier, rules) {
    //         var trigger = {},
    //             trigger.state = false;
    //         trigger.identifier = triggerIdentifier;
    //         trigger.rules = rules,
    //             trigger.ruleTable = {};
    //         for (var i = 0; i < rules.length; ++i) {
    //             var rule = rules[i];
    //             trigger.ruleTable[rule.ruleIdentifier] = rule;
    //         }
	//
    //         return trigger;
    //     },
    //     createRule: function(ruleUpdateFunction) {
    //         var rule = {};
    //         rule.state = false;
    //         rule.ruleType = $this.triggers.RuleTypeGeneric;
    //         rule.ruleUpdateFunction = ruleUpdateFunction;
    //         rule.ruleIdentifier = 'Rule' + (++ruleCounter);
    //         return rule;
    //     },
    //     createRuleForNearable: function(nearableIdentifierOrType, ruleUpdateFunction) {
    //         var rule = $this.triggers.createRule(ruleUpdateFunction),
    //             if (typeof(nearableIdentifierOrType) == 'string') {
    //                 rule.ruleType = $this.triggers.RuleTypeNearableIdentifier;
    //                 rule.nearableIdentifier = nearableIdentifierOrType;
    //             } else
    //         if (typeof( )nearableIdentifierOrType) == 'number') {
    //             rule.ruleType = $this.triggers.RuleTypeNearableType;
    //             rule.nearableType = nearableIdentifierOrType;
    //         } else {
    //             return null;
    //         }
	//
    //         return rule;
    //     },
    //     createRuleForInRangeOfNearable: function(nearableIdentifierOrType) {
    //         var rule = $this.triggers.createRuleForNearable(
    //                 nearableIdentifierOrType,
    //                 null),
    //             if (typeof(nearableIdentifierOrType) == 'string') {
    //                 rule.ruleType = $this.triggers.RuleTypeInRangeOfNearableIdentifier;
    //                 rule.nearableIdentifier = nearableIdentifierOrType;
    //             } else
    //         if (typeof(nearableIdentifierOrType) == 'number') {
    //             rule.ruleType = $this.triggers.RuleTypeInRangeOfNearableType;
    //             rule.nearableType = nearableIdentifierOrType;
    //         }
	//
    //         return rule;
    //     },
    //     createRuleForOutsideRangeOfNearable: function(nearableIdentifierOrType) {
    //         var rule = $this.triggers.createRuleForNearable(
    //                 nearableIdentifierOrType,
    //                 null),
    //             if (typeof(nearableIdentifierOrType) == 'string') {
    //                 rule.ruleType = $this.triggers.RuleTypeOutsideRangeOfNearableIdentifier;
    //                 rule.nearableIdentifier = nearableIdentifierOrType;
    //             } else
    //         if (typeof(nearableIdentifierOrType) == 'number') {
    //             rule.ruleType = $this.triggers.RuleTypeOutsideRangeOfNearableType;
    //             rule.nearableType = nearableIdentifierOrType;
    //         }
	//
    //         return rule;
    //     },
    //     startMonitoringForTrigger: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         var triggerSuccess = obj.success;
    //         obj.success = function(event) {
    //             if (event.triggerIdentifier == trigger.identifier) {
    //                 if ('triggerChangedState' == event.eventType) {
    //                     trigger.state = event.triggerState;
    //                     triggerSuccess(trigger);
    //                 } else if ('update' == event.eventType) {
    //                     HelperUtils.helper_updateTriggerRule(trigger, event);
    //                 }
    //             }
    //         };
	//
    //         var triggerObject = HelperUtils.helper_createTriggerObject(trigger),
    //             HelperUtils.cordova.exec(obj, 'triggers_startMonitoringForTrigger',
    //                 obj.data // [triggerObject]
    //             ),
    //             return true;
    //     },
    //     stopMonitoringForTrigger: function(obj) {
    //         obj = HelperUtils.processParams(obj);
    //         HelperUtils.cordova.exec(obj, 'triggers_stopMonitoringForTrigger',
    //                 obj.data // [trigger.identifier]
    //             ),
    //             return true;
    //     },
    // };
	//
    // this.triggers.rules = {
    //     nearableIsMoving: function() {
    //         return function(rule, nearable) {
    //             rule.state = nearable && nearable.isMoving;
    //         };
    //     },
    //     nearableIsNotMoving: function() {
    //         return function(rule, nearable) {
    //             rule.state = nearable && !nearable.isMoving;
    //         };
    //     },
    //     nearableTemperatureBetween: function(low, high) {
    //         return function(rule, nearable) {
    //             rule.state =
    //                 nearable &&
    //                 (nearable.temperature >= low) &&
    //                 (nearable.temperature <= high);
    //         };
    //     },
    //     nearableTemperatureLowerThan: function(temp) {
    //         return function(rule, nearable) {
    //             rule.state =
    //                 nearable &&
    //                 (nearable.temperature < temp);
    //         };
    //     },
    //     nearableTemperatureGreaterThan: function(temp) {
    //         return function(rule, nearable) {
    //             console.log('nearable.temperature :' + nearable.temperature)
    //             rule.state =
    //                 nearable &&
    //                 (nearable.temperature > temp);
    //         };
    //     },
    //     nearableIsClose: function() {
    //         return function(rule, nearable, event) {
    //             if (!nearable) {
    //                 rule.state = false;
    //                 return;
    //             }
	//
    //             if (!nearable) {
    //                 rule.notCloseTracker = 0;
    //             }
	//
    //             if (nearable.zone != Estimote.nearables.$this.nearables.NearableZoneImmediate &&
    //                 nearable.zone != Estimote.nearables.$this.nearables.NearableZoneNear) {
    //                 ++rule.notCloseTracker;
    //             } else {
    //                 rule.notCloseTracker = 0;
    //             }
	//
    //             rule.state = rule.notCloseTracker < 5;
    //         };
    //     },
    //     nearableIsInRange: function() {
    //         return function(rule, nearable, event) {
    //             if (!nearable) {
    //                 rule.state = false;
    //                 return;
    //             }
	//
    //             if (!rule.notInRangeTracker) {
    //                 rule.notInRangeTracker = 0;
    //             }
	//
    //             if (nearable.zone == Estimote.nearables.$this.nearables.NearableZoneUnknown) {
    //                 ++rule.notInRangeTracker;
    //             } else {
    //                 rule.notInRangeTracker = 0;
    //             }
	//
    //             rule.state = rule.notInRangeTracker < 5;
    //         };
    //     }
    // };
};

module.exports = new _estimote();
