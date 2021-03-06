/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
'use strict';

// Controller for creating a new event. This is a wizard, so it implements a state machine design pattern.
angular.module('adminNg.controllers')
.controller('NewEventCtrl', ['$scope', 'NewEventStates', 'NewEventResource', 'EVENT_TAB_CHANGE', 'Notifications', 'Modal',
 function ($scope, NewEventStates, NewEventResource, EVENT_TAB_CHANGE, Notifications, Modal) {
    $scope.states = NewEventStates.get();
    // This is a hack, due to the fact that we need to read html from the server :(
    // Shall be banished ASAP

    var metadata,
        accessController,
        // Reset all the wizard states
        resetStates = function () {
            angular.forEach($scope.states, function(state)  {
                if (angular.isDefined(state.stateController.reset)) {
                    state.stateController.reset();
                }
            });
        };

    angular.forEach($scope.states, function (state) {
        if (state.stateController.isAccessState) {
            accessController = state.stateController;
        } else if (state.stateController.isMetadataState) {
            metadata = state.stateController;
        }
    });

    if (angular.isDefined(metadata) && angular.isDefined(accessController)) {
        accessController.setMetadata(metadata);
    }

    $scope.$on(EVENT_TAB_CHANGE, function (event, args) {
        if (args.old !== args.current && args.old.stateController.isProcessingState) {
            args.old.stateController.save();
        }

        if (args.current.stateController.isAccessState) {
            args.current.stateController.loadSeriesAcl();
        }
    });

    $scope.submit = function () {
        var messageId, userdata = { metadata: []}, ace = [];

        window.onbeforeunload = function (e) {
            var confirmationMessage = 'The file has not completed uploading.';

            (e || window.event).returnValue = confirmationMessage;     //Gecko + IE
            return confirmationMessage;                                //Webkit, Safari, Chrome etc.
        };
        
        angular.forEach($scope.states, function (state) {

            if (state.stateController.isMetadataState) {
                for (var o in state.stateController.ud) {
                    if (state.stateController.ud.hasOwnProperty(o)) {
                        userdata.metadata.push(state.stateController.ud[o]);
                    }
                }
            } else if (state.stateController.isAccessState) {
                angular.forEach(state.stateController.ud.policies, function (policy) {
                    if (angular.isDefined(policy.role)) {
                        if (policy.read) {
                            ace.push({
                                'action' : 'read',
                                'allow'  : policy.read,
                                'role'   : policy.role
                            });
                        }

                        if (policy.write) {
                            ace.push({
                                'action' : 'write',
                                'allow'  : policy.write,
                                'role'   : policy.role
                            });
                        }
                    }
                });

                userdata.access = {
                    acl: {
                        ace: ace
                    }
                };
            } else {
                userdata[state.name] = state.stateController.ud;
            }
        });

        NewEventResource.save({}, userdata, function () {
            Notifications.add('success', 'EVENTS_CREATED');
            Notifications.remove(messageId);
            resetStates();
            window.onbeforeunload = null;
        }, function () {
            Notifications.add('error', 'EVENTS_NOT_CREATED');
            Notifications.remove(messageId);
            resetStates();
            window.onbeforeunload = null;
        });

        // close will also make the Table.fetch()
        Modal.$scope.close();
        // add message that never disappears
        messageId = Notifications.add('success', 'EVENTS_UPLOAD_STARTED', 'global', -1);
    };
}]);
