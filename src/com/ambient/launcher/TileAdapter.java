package com.ambient.launcher;

import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/** App tiles for the dock and the grid. Focus is drawn by HomeActivity, not here. */
final class TileAdapter extends RecyclerView.Adapter<TileAdapter.Holder> {
    interface Binder {
        void onBound(Holder h, int position);
    }

    final List<App> apps = new ArrayList<>();
    private final boolean names;
    private final Binder binder;

    TileAdapter(boolean names, Binder binder) {
        this.names = names;
        this.binder = binder;
    }

    void set(List<App> list) {
        apps.clear();
        apps.addAll(list);
        notifyDataSetChanged();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView banner;
        final TextView name;

        Holder(View v) {
            super(v);
            banner = v.findViewById(R.id.banner);
            name = v.findViewById(R.id.name);
        }
    }

    private static final ViewOutlineProvider ROUNDED = new ViewOutlineProvider() {
        @Override
        public void getOutline(View v, Outline o) {
            o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 10 * v.getResources().getDisplayMetrics().density);
        }
    };

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int type) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.tile, parent, false);
        Holder h = new Holder(v);
        h.banner.setOutlineProvider(ROUNDED);
        h.banner.setClipToOutline(true);
        if (names) v.getLayoutParams().height = parent.getResources().getDimensionPixelSize(R.dimen.grid_pitch);
        else h.name.setVisibility(View.GONE);
        return h;
    }

    @Override
    public void onBindViewHolder(Holder h, int position) {
        App a = apps.get(position);
        if (a.banner != null) {
            h.banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            h.banner.setPadding(0, 0, 0, 0);
            h.banner.setImageDrawable(a.banner);
        } else {
            // No TV banner: the icon, centred on the dark card.
            int pad = (int) (30 * h.banner.getResources().getDisplayMetrics().density);
            h.banner.setScaleType(ImageView.ScaleType.FIT_CENTER);
            h.banner.setPadding(pad, pad / 2, pad, pad / 2);
            h.banner.setImageDrawable(a.icon);
        }
        // An input tile's banner already shows its name.
        h.name.setText(a.pkg.startsWith(App.INPUT) ? "" : a.label);
        binder.onBound(h, position);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }
}
