package com.example.avp.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.SparseArray;

import com.gdrive.GDriveService;
import com.gdrive.GDriveWrapper;
import com.gdrive.GoogleAccountHolder;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import at.huber.youtubeExtractor.VideoMeta;
import at.huber.youtubeExtractor.YouTubeExtractor;
import at.huber.youtubeExtractor.YtFile;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class ExoPlayerModel {
    private final Context context;
    private final String linkOnVideo;

    public ExoPlayerModel(Context context, String linkOnVideo) {
        this.context = context;
        this.linkOnVideo = linkOnVideo.isEmpty()
                ? "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                : linkOnVideo;
        // TODO: load state (speed, play in the background)
    }

    public Observable<MediaSource> getMediaSource() {
        Observable<MediaSource> res;
        if (isYoutubeUrl(linkOnVideo)) {
            res = getObservableMediaSourceFromYouTube();
        } else if (GDriveService.isGDriveURL(linkOnVideo)) {
            res = getObservableMediaSourceFromGDrive();
        } else {
            res = getObservableMediaSourceFromUri();
        }

        return res.doOnError(e -> {
            // TODO: send message to the application, that link is incorrect
        });
    }

    private Observable<MediaSource> getObservableMediaSourceFromUri() {
        AVPMediaMetaData meta = new AVPMediaMetaData(new File(linkOnVideo).getName(), null, linkOnVideo, null);
        return Observable.just(new DefaultMediaSourceFactory(context)
                .createMediaSource(new MediaItem.Builder()
                        .setUri(Uri.parse(linkOnVideo))
                        .setTag(meta)
                        .build()));
    }

    private static boolean isYoutubeUrl(String youTubeURl) {
        String pattern = "^(http(s)?://)?((w){3}.)?youtu(be|.be)?(\\.com)?/.+";
        return !youTubeURl.isEmpty() && youTubeURl.matches(pattern);
    }

    private Observable<MediaSource> getObservableMediaSourceFromGDrive() {
        PublishSubject<MediaSource> resObservable = PublishSubject.create(); // create a list to save result MediaSource

        GoogleSignInAccount account = GoogleAccountHolder.getInstance().getAccount();
        if (account == null) {
            throw new IllegalStateException("User is not logged in to the google account.");
        }
        GDriveService driveService = new GDriveService(context, account);

        Task<InputStream> fileTask = driveService.getDownloadStreamOnFile(linkOnVideo);
        fileTask.addOnSuccessListener(fileStream -> {

            DataSpec dataSpec = new DataSpec(Uri.parse(linkOnVideo));
            GoogleDriveVideoDataSource dataSource = new GoogleDriveVideoDataSource(dataSpec, driveService);

            DataSource.Factory factory = () -> dataSource;

            /*String title = gDriveWrapper.getTitle(fileID);
            String author = gDriveWrapper.getAuthor(fileID);
            String previewURL = gDriveWrapper.getPreviewURL(fileID);*/

            //TODO: add metadata from gdrive
            //AVPMediaMetaData meta = new AVPMediaMetaData(title, author, null, previewURL);
            MediaSource fileSource = new ProgressiveMediaSource
                    .Factory(factory)
            //        .setTag(meta)
                    .createMediaSource(MediaItem.fromUri(dataSource.getUri()));
            resObservable.onNext(fileSource);
        });

        return resObservable;
    }

    @SuppressLint("StaticFieldLeak")
    private Observable<MediaSource> getObservableMediaSourceFromYouTube() {
        PublishSubject<MediaSource> resObservable = PublishSubject.create(); // create a list to save result MediaSource
        new YouTubeExtractor(context) {
            private int getBestPossibleVideoTag(SparseArray<YtFile> ytFiles) {
                List<Integer> videoTags = Arrays.asList(
                        266, // 2160p
                        264, // 1440p
                        299, // 1080p60
                        137, // 1080p
                        298, // 720p60
                        136, // 720p
                        135, // 480p
                        134, // 360p
                        133, // 240p
                        160  // 144p
                );
                for (Integer videoTag : videoTags) {
                    YtFile ytFile = ytFiles.get(videoTag);
                    if (ytFile != null) {
                        return videoTag;
                    }
                }
                throw new RuntimeException("No video in ytFiles");
            }

            @Override
            protected void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta videoMeta) {
                if (ytFiles != null) {
                    int videoTag = getBestPossibleVideoTag(ytFiles);
                    int audioTag = 140; // audio tag for m4a

                    AVPMediaMetaData mediaMetadata = new AVPMediaMetaData(videoMeta.getTitle(), videoMeta.getAuthor(), linkOnVideo,
                            videoMeta.getMqImageUrl());

                    MediaSource audioSource = new ProgressiveMediaSource
                            .Factory(new DefaultHttpDataSource.Factory())
                            .createMediaSource(new MediaItem.Builder()
                                    .setUri(ytFiles.get(audioTag).getUrl())
                                    .setTag(mediaMetadata)
                                    .build());

                    MediaSource videoSource = new ProgressiveMediaSource
                            .Factory(new DefaultHttpDataSource.Factory())
                            .createMediaSource(new MediaItem.Builder()
                                    .setUri(ytFiles.get(videoTag).getUrl())
                                    .setTag(mediaMetadata)
                                    .build());
                    MediaSource resMediaSource = new MergingMediaSource(true, videoSource, audioSource);
                    resObservable.onNext(resMediaSource);
                }
            }
        }.extract(linkOnVideo, true, true);

        return resObservable;
    }
}
