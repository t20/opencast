// The main controller that all other scopes inherit from (except isolated scopes).
angular.module('adminNg.controllers')
.controller('ApplicationCtrl', ['$scope', '$rootScope', '$location', '$window', 'AuthService', 'Notifications', 'ResourceModal', 'VersionResource',
    function ($scope, $rootScope, $location, $window, AuthService, Notifications, ResourceModal, VersionResource) {

        $scope.bodyClicked = function () {
            angular.element('[old-admin-ng-dropdown]').removeClass('active');
        };

        var FEEDBACK_URL_PROPERTY = 'org.opencastproject.admin.feedback.url',
            DOCUMENTATION_URL_PROPERTY = 'org.opencastproject.admin.documentation.url',
            RESTDOCS_URL_PROPERTY = 'org.opencastproject.admin.restdocs.url';

        $scope.currentUser  = null;
        $scope.feedbackUrl = undefined;
        $scope.documentationUrl = 'http://docs.opencast.org';
        $scope.restdocsUrl = '/rest_docs.html';

        AuthService.getUser().$promise.then(function (user) {
            $scope.currentUser = user;

            if (angular.isDefined(user.org.properties[FEEDBACK_URL_PROPERTY])) {
                $scope.feedbackUrl = user.org.properties[FEEDBACK_URL_PROPERTY];
            }

            if (angular.isDefined(user.org.properties[DOCUMENTATION_URL_PROPERTY])) {
                $scope.documentationUrl = user.org.properties[DOCUMENTATION_URL_PROPERTY];
            }

            if (angular.isDefined(user.org.properties[RESTDOCS_URL_PROPERTY])) {
                $scope.restdocsUrl = user.org.properties[RESTDOCS_URL_PROPERTY];
            }
        });

        $scope.toURL = function (url) {
            $window.location.href = url;
        };

        $rootScope.userIs = AuthService.userIsAuthorizedAs;

        // Restore open modals if any
        var params = $location.search();
        if (params.modal && params.resourceId) {
            ResourceModal.show(params.modal, params.resourceId, params.tab, params.action);
        }

        if (angular.isUndefined($rootScope.version)) {
            VersionResource.query(function(response) {
                $rootScope.version = response.version ? response : (angular.isArray(response.versions)?response.versions[0]:{});
            });
        }
    }
]);
