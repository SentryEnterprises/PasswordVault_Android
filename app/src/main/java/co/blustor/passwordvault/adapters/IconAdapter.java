package co.blustor.passwordvault.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

import co.blustor.passwordvault.R;
import co.blustor.passwordvault.utils.MyApplication;

public class IconAdapter extends BaseAdapter {
    private Context mContext;

    public IconAdapter(Context context) {
        mContext = context;
    }

    @Override
    public int getCount() {
        return 69;
    }

    @Override
    public Object getItem(int position) {
        return position;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater layoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view;
        if (convertView == null) {
            view = layoutInflater.inflate(R.layout.grid_icon, parent, false);
        } else {
            view = convertView;
        }

        ImageView iconImageView = (ImageView) view.findViewById(R.id.imageview_icon);
        iconImageView.setImageResource(MyApplication.getIcons().get(position));

        TextView iconIdTextView = (TextView) view.findViewById(R.id.textview_icon_id);
        iconIdTextView.setText(String.format(Locale.getDefault(), "%02d", position));

        return view;
    }
}
