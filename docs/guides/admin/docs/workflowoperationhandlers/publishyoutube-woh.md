# PublishYouTubeWorkflowOperationHandler

## Description
The PublishYouTubeWorkflowOperationHandler builds playlists at YouTube by publishing recordings to the playlist id associated with the series in Matterhorn.

Considerations that accompany this operation:
* YouTube playlist id is stored as metadata within Series; YouTube video id is stored in episode metadata.
* Audio-only recordings must be converted to video with cover image in order to qualify for YouTube publish.
* RetractYouTubeWorkflowOperationHandler will remove a video from YouTube playlist.
* When recording includes two video files (e.g., video of lecturer and video of slides) they must be merged into one in order to publish the set to YouTube.
* The Google API provides a retry-on-failure framework which the matterhorn-publication-service-youtube-v3 code employs. See the *org.opencastproject.publication.youtube.retryPolicy* configuration.

## Parameter Table

|configuration keys|example|description|default value|
|------------------|-------|-----------|-------------|
|org.opencastproject.publication.youtube.category|'Education'|[Read about video categories at YouTube](https://developers.google.com/youtube/v3/docs/videos)|'CHANGE_ME'|
|org.opencastproject.publication.youtube.keywords|'Berkeley, University of California'|[Read about keywords at YouTube](https://developers.google.com/youtube/v3/docs/videos)|'CHANGE_ME'|
|org.opencastproject.publication.youtube.makeVideosPrivate=true|example|[Read about YouTube's privacyStatus](https://developers.google.com/youtube/v3/docs/videos)|true|
|org.opencastproject.publication.youtube.defaultPlaylist|'UC Berkeley miscellaneous'|If no playlist id is associated with the series then add video to this playlist.|'CHANGE_ME'|
|org.opencastproject.publication.youtube.maxFieldLength|74|Read Google documentation on max length|74|
|org.opencastproject.publication.youtube.credentialDatastore|`see default value`|Name for the data store|'store'|
| org.opencastproject.publication.youtube.clientSecretsV3 |`see default value`| [Description](https://developers.google.com/api-client-library/python/guide/aaa_client_secrets) | \$\{bundles.configuration.location\}/youtube-v3/client-secrets-youtube-v3.json |
| org.opencastproject.publication.youtube.dataStore=${org.opencastproject.storage.dir}/youtube-v3/data-store
| org.opencastproject.publication.youtube.retryPolicy |`see default value`| [Description](https://opencast.jira.com/browse/MH-10863) | First failure:0:0:1,Second failure:900000:0:1,Third failure:3600000:0:1,Fourth failure:7200000:7200000:12 |

## Operation Example
```
<operation
        if="${youtube}"
        id="compose"
        retry-strategy="retry"
        max-attempts="2"
        fail-on-error="true"
        exception-handler-workflow="error"
        description="Create cover image for YouTube">
  <configurations>
    <configuration key="audio-only">true</configuration>
    <configuration key="source-flavors">presenter/trimmed</configuration>
    <configuration key="target-flavor">presenter/AudioOnly</configuration>
    <configuration key="encoding-profile">mux-audio-image</configuration>
  </configurations>
</operation>
```