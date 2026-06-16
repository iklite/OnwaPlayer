package com.ikechi.studio.onwa.player.fragment;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.ikechi.studio.IkLog;
import com.ikechi.studio.onwa.player.MainActivity;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.models.AudioItem;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MetadataEditorFragment extends Fragment {

    private static final String TAG = "MetadataEditorFragment";

    private EditText etTitle, etArtist, etAlbum, etGenre, etYear;
    private Button btnSave, btnCancel;
    private MaterialToolbar toolbar;

    private Uri audioUri;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        IkLog.setInstantFlush(true);
        IkLog.d(TAG, "onCreateView called");
        View view = null;
        try {
            view = inflater.inflate(R.layout.fragment_metadata_editor, container, false);
            if (view == null) {
                IkLog.e(TAG, "onCreateView: inflated view is NULL");
            } else {
                IkLog.d(TAG, "onCreateView: layout inflated successfully");
            }
        } catch (Exception e) {
            IkLog.e(TAG, "onCreateView: inflation failed", e);
            Toast.makeText(requireContext(), "Failed to load metadata editor", Toast.LENGTH_SHORT).show();
            return null;
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        IkLog.d(TAG, "onViewCreated");

        if (view instanceof android.widget.TextView) {
            IkLog.w(TAG, "Fallback error view active, skipping init");
            return;
        }

        try {
            toolbar  = view.findViewById(R.id.toolbar);
            etTitle  = view.findViewById(R.id.et_meta_title);
            etArtist = view.findViewById(R.id.et_meta_artist);
            etAlbum  = view.findViewById(R.id.et_meta_album);
            etGenre  = view.findViewById(R.id.et_meta_genre);
            etYear   = view.findViewById(R.id.et_meta_year);
            btnSave  = view.findViewById(R.id.btn_meta_save);
            btnCancel = view.findViewById(R.id.btn_meta_cancel);

            if (toolbar == null) {
                IkLog.e(TAG, "Toolbar not found in layout");
            } else {
                toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getParentFragmentManager().popBackStack();
                    }
                });
                IkLog.d(TAG, "Toolbar setup complete");
            }

            if (etTitle == null) IkLog.e(TAG, "et_meta_title missing");
            if (etArtist == null) IkLog.e(TAG, "et_meta_artist missing");
            if (etAlbum == null) IkLog.e(TAG, "et_meta_album missing");
            if (etGenre == null) IkLog.e(TAG, "et_meta_genre missing");
            if (etYear == null) IkLog.e(TAG, "et_meta_year missing");
            if (btnSave == null) IkLog.e(TAG, "btn_meta_save missing");
            if (btnCancel == null) IkLog.e(TAG, "btn_meta_cancel missing");

            // Get current playing URI from activity
            Activity activity = getActivity();
            if (activity instanceof MainActivity) {
                audioUri = ((MainActivity) activity).getCurrentUri();
                if (audioUri == null) {
                    IkLog.w(TAG, "Current URI is null – no track playing");
                } else {
                    IkLog.d(TAG, "Current URI: " + audioUri);
                }
                String title  = ((MainActivity) activity).getCurrentTitle();
                String artist = ((MainActivity) activity).getCurrentArtist();
                String album  = ((MainActivity) activity).getCurrentAlbum();
                if (etTitle != null)  etTitle.setText(title);
                if (etArtist != null) etArtist.setText(artist);
                if (etAlbum != null)  etAlbum.setText(album);
                IkLog.d(TAG, "Pre-filled title=" + title + ", artist=" + artist + ", album=" + album);
            } else {
                IkLog.e(TAG, "Activity is not MainActivity");
            }

            if (btnSave != null) {
                btnSave.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        saveMetadata();
                    }
                });
            }
            if (btnCancel != null) {
                btnCancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getParentFragmentManager().popBackStack();
                    }
                });
            }

            IkLog.d(TAG, "MetadataEditorFragment initialized successfully");
        } catch (Exception e) {
            IkLog.e(TAG, "Error during initialization", e);
        }
    }

    public void setAudioData(@Nullable AudioItem item) {
        if (item != null) {
            audioUri = item.getUri();
            String title  = item.getTitle();
            String artist = item.getArtist();
            String album  = item.getAlbum();
            if (etTitle != null)  etTitle.setText(title);
            if (etArtist != null) etArtist.setText(artist);
            if (etAlbum != null)  etAlbum.setText(album);
        }
    }

    private void saveMetadata() {
        if (audioUri == null) {
            Toast.makeText(getContext(), "No track selected", Toast.LENGTH_SHORT).show();
            IkLog.w(TAG, "saveMetadata: audioUri is null");
            return;
        }

        final String newTitle  = etTitle.getText().toString().trim();
        final String newArtist = etArtist.getText().toString().trim();
        final String newAlbum  = etAlbum.getText().toString().trim();
        final String newGenre  = etGenre.getText().toString().trim();
        final String newYear   = etYear.getText().toString().trim();

        IkLog.d(TAG, "Saving metadata: title=" + newTitle + ", artist=" + newArtist + ", album=" + newAlbum
                + ", genre=" + newGenre + ", year=" + newYear);

        btnSave.setEnabled(false);
        btnSave.setText("Saving…");

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean mediaStoreOk = false;
                boolean jaudioOk = false;

                // 1. Update MediaStore (API 29+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        ContentValues values = new ContentValues();
                        if (!newTitle.isEmpty())  values.put(MediaStore.Audio.Media.TITLE, newTitle);
                        if (!newArtist.isEmpty()) values.put(MediaStore.Audio.Media.ARTIST, newArtist);
                        if (!newAlbum.isEmpty())  values.put(MediaStore.Audio.Media.ALBUM, newAlbum);
                        if (!newGenre.isEmpty())  values.put(MediaStore.Audio.Media.GENRE, newGenre);
                        if (!newYear.isEmpty()) {
                            try {
                                values.put(MediaStore.Audio.Media.YEAR, Integer.parseInt(newYear));
                            } catch (NumberFormatException ignored) {}
                        }
                        int updated = getContext().getContentResolver().update(audioUri, values, null, null);
                        IkLog.d(TAG, "MediaStore updated rows: " + updated);
                        mediaStoreOk = (updated > 0);
                    } catch (Exception e) {
                        IkLog.e(TAG, "MediaStore update failed", e);
                    }
                } else {
                    IkLog.d(TAG, "Skipping MediaStore update (API < 29)");
                }

                // 2. Update embedded tags with Jaudiotagger
                try {
                    updateEmbeddedTags(newTitle, newArtist, newAlbum, newGenre, newYear);
                    jaudioOk = true;
                } catch (Exception e) {
                    IkLog.e(TAG, "Jaudiotagger update failed", e);
                }

                final boolean anySuccess = mediaStoreOk || jaudioOk;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        btnSave.setEnabled(true);
                        btnSave.setText("Save");
                        if (anySuccess) {
                            Toast.makeText(getContext(), "Metadata saved", Toast.LENGTH_SHORT).show();
                            getParentFragmentManager().popBackStack();
                        } else {
                            Toast.makeText(getContext(), "Save failed. File may not be writable.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }, "metadata-saver").start();
    }

    private void updateEmbeddedTags(String title, String artist, String album,
                                    String genre, String year) throws Exception {
        ContentResolver resolver = getContext().getContentResolver();
        File tempFile = new File(getContext().getCacheDir(), "temp_audio_edit");
        IkLog.d(TAG, "Starting Jaudiotagger update, temp file: " + tempFile);

        InputStream is = null;
        OutputStream os = null;
        try {
            is = resolver.openInputStream(audioUri);
            if (is == null) {
                throw new Exception("openInputStream returned null for " + audioUri);
            }
            os = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
            is.close();
            os.close();
            IkLog.d(TAG, "Copied to temp file, size=" + tempFile.length());

            // Read the audio file – works for MP3, FLAC, OGG, etc.
            AudioFile audioFile = AudioFileIO.read(tempFile);
            Tag tag = audioFile.getTag();

            // If the file has no tag at all, create one
            if (tag == null) {
                tag = new ID3v24Tag();
                IkLog.d(TAG, "Created new ID3v24 tag");
            }

            // Set the tag fields
            if (!title.isEmpty())  tag.setField(FieldKey.TITLE, title);
            if (!artist.isEmpty()) tag.setField(FieldKey.ARTIST, artist);
            if (!album.isEmpty())  tag.setField(FieldKey.ALBUM, album);
            if (!genre.isEmpty())  tag.setField(FieldKey.GENRE, genre);
            if (!year.isEmpty())   tag.setField(FieldKey.YEAR, year);

            // ★ Attach the tag to the audio file (this also sets audioFile.tag)
            audioFile.setTag(tag);
            // Write the changes back to the temp file
            AudioFileIO.write(audioFile);
            IkLog.d(TAG, "Tags written to temp file");

            // Copy back to original URI
            InputStream inStream = null;
            OutputStream outStream = null;
            try {
                inStream = new FileInputStream(tempFile);
                outStream = resolver.openOutputStream(audioUri, "wt");
                if (outStream == null) {
                    throw new Exception("openOutputStream returned null for " + audioUri);
                }
                byte[] buf = new byte[8192];
                int length;
                while ((length = inStream.read(buf)) > 0) {
                    outStream.write(buf, 0, length);
                }
                IkLog.d(TAG, "Modified file copied back to original URI");
            } finally {
                if (inStream != null)  inStream.close();
                if (outStream != null) outStream.close();
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
                IkLog.d(TAG, "Temp file deleted");
            }
        }
    }
}