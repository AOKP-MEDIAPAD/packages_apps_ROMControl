package com.aokp.romcontrol.performance;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Switch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.aokp.romcontrol.R;

import com.aokp.romcontrol.util.CMDProcessor;

public class VoltageControlSettings extends Fragment {

    private static final String TAG = "VoltageControlActivity";

    public static final String KEY_APPLY_BOOT = "apply_voltages_at_boot";
    public static final String MV_TABLE0 = "/sys/devices/system/cpu/cpufreq/vdd_table/vdd_levels";
    public static final String MV_TABLE1 = "/sys/devices/system/cpu/cpu1/cpufreq/UV_mV_table";
    public static final String MV_TABLE2 = "/sys/devices/system/cpu/cpu2/cpufreq/UV_mV_table";
    public static final String MV_TABLE3 = "/sys/devices/system/cpu/cpu3/cpufreq/UV_mV_table";
    public static final int DIALOG_EDIT_VOLT = 0;
    private List<Voltage> mVoltages;
    private ListAdapter mAdapter;
    private static SharedPreferences preferences;
    private Voltage mVoltage;
    private Activity mActivity;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup root, Bundle savedInstanceState) {
        mActivity = getActivity();
        View view = inflater.inflate(R.xml.voltage_settings, root, false);
        preferences = PreferenceManager.getDefaultSharedPreferences(mActivity);

        final ListView listView = (ListView) view.findViewById(R.id.ListView);
        final Switch setOnBoot = (Switch) view.findViewById(R.id.applyAtBoot);
        mAdapter = new ListAdapter(mActivity);
        mVoltages = getVolts(preferences);

        if (mVoltages.isEmpty()) {
            ((TextView) view.findViewById(R.id.emptyList))
                    .setVisibility(View.VISIBLE);
            ((LinearLayout) view.findViewById(R.id.BottomBar))
                    .setVisibility(View.GONE);
        }

        setOnBoot.setChecked(preferences.getBoolean(KEY_APPLY_BOOT, false));
        setOnBoot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                final SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(KEY_APPLY_BOOT, isChecked);
                editor.commit();
            }
        });

        ((Button) view.findViewById(R.id.applyBtn))
                .setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
	         for (final Voltage volt : mVoltages) {
                    new CMDProcessor().su.runWaitFor("busybox echo '"
                            + volt.getFreq() + " " + volt.getSavedMV()
                            + "' > " + MV_TABLE0);
                 }
	        final List<Voltage> volts = getVolts(preferences);
                mVoltages.clear();
                mVoltages.addAll(volts);
                mAdapter.notifyDataSetChanged();
            }
        });

        mAdapter.setListItems(mVoltages);
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                    long id) {
                mVoltage = mVoltages.get(position);
                showDialog(DIALOG_EDIT_VOLT);
            }
        });

        return view;
    }

    public static List<Voltage> getVolts(final SharedPreferences preferences) {
        final List<Voltage> volts = new ArrayList<Voltage>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(MV_TABLE0), 256);
            String line = "";
            while ((line = br.readLine()) != null) {
                final String[] values = line.trim().split("\\s+");
                if (values != null) {
                    if (values.length >= 2) {
                        final String freq = values[0].replace(":", "");
                        final String currentMv = values[1];
                        final String savedMv = preferences.getString(freq, currentMv);
                        final Voltage voltage = new Voltage();
                        voltage.setFreq(freq);
                        voltage.setCurrentMV(currentMv);
                        voltage.setSavedMV(savedMv);
                        volts.add(voltage);
                    }
                }
            }
            br.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, MV_TABLE0 + " does not exist");
        } catch (IOException e) {
            Log.d(TAG, "Error reading " + MV_TABLE0);
        }
        return volts;
    }

    private static final int[] STEPS = new int[] {
            812500, 875000, 887500, 912500, 925000, 937500,
            950000, 975000, 1000000, 1012500, 1037500, 1050000, 1062500, 1075000, 1087500,
            1100000, 1125000, 1150000, 1175000, 1187500, 1200000, 1225000, 1250000, 1275000, 1300000
    };

    private static int getNearestStepIndex(final int value) {
        int index = 0;
        for (int i = 0; i < STEPS.length; i++) {
            if (value > STEPS[i]) {
                index++;
            } else {
                break;
            }
        }
        return index;
    }

    protected void showDialog(final int id) {
        AlertDialog dialog = null;
        switch (id) {
            case DIALOG_EDIT_VOLT:
                final LayoutInflater factory = LayoutInflater.from(mActivity);
                final View voltageDialog = factory.inflate(R.layout.voltage_dialog, null);

                final EditText voltageEdit = (EditText) voltageDialog
                        .findViewById(R.id.voltageEdit);
                final SeekBar voltageSeek = (SeekBar) voltageDialog.findViewById(R.id.voltageSeek);
                final TextView voltageMeter = (TextView) voltageDialog
                        .findViewById(R.id.voltageMeter);

                final String savedMv = mVoltage.getSavedMV();
                final int savedVolt = Integer.parseInt(savedMv);
                voltageEdit.setText(savedMv);
                voltageEdit.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void afterTextChanged(Editable arg0) {
                        // TODO Auto-generated method stub
                    }

                    @Override
                    public void beforeTextChanged(CharSequence arg0, int arg1,
                            int arg2, int arg3) {
                        // TODO Auto-generated method stub
                    }

                    @Override
                    public void onTextChanged(CharSequence arg0, int arg1,
                            int arg2, int arg3) {
                        final String text = voltageEdit.getText().toString();
                        int value = 0;
                        try {
                            value = Integer.parseInt(text);
                        } catch (NumberFormatException nfe) {
                            return;
                        }
                        voltageMeter.setText(text);
                        final int index = getNearestStepIndex(value);
                        voltageSeek.setProgress(index);
                    }

                });

                voltageMeter.setText(savedMv);
                voltageSeek.setMax(24);
                voltageSeek.setProgress(getNearestStepIndex(savedVolt));
                voltageSeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar sb, int progress,
                            boolean fromUser) {
                        if (fromUser) {
                            final String volt = Integer.toString(STEPS[progress]);
                            voltageMeter.setText(volt);
                            voltageEdit.setText(volt);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // TODO Auto-generated method stub

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        // TODO Auto-generated method stub

                    }

                });

                dialog = new AlertDialog.Builder(mActivity)
                        .setTitle(mVoltage.getFreq() + getResources().getString(R.string.ps_volt_mhz_voltage))
                        .setView(voltageDialog)
                        .setPositiveButton(getResources().getString(R.string.ps_volt_save), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        removeDialog(id);
                        final String value = voltageEdit.getText().toString();
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString(mVoltage.getFreq(), value);
                        editor.commit();
                        mVoltage.setSavedMV(value);
                        mAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(null,
                        new  DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                            int whichButton) {
                        removeDialog(id);
                    }
                }).create();
                break;
            default:
                break;
        }

        if (dialog != null) {
            FragmentManager fm = getActivity().getFragmentManager();
            FragmentTransaction ftr = fm.beginTransaction();
            CustomDialogFragment newFragment = CustomDialogFragment.newInstance(dialog);
            DialogFragment fragmentDialog = (DialogFragment) fm.findFragmentByTag("" + id);
            if (fragmentDialog != null) {
                ftr.remove(fragmentDialog);
                ftr.commit();
            }
            newFragment.show(fm, "" + id);
        }
    }

    protected void removeDialog(int pDialogId) {
        FragmentManager fm = mActivity.getFragmentManager();
        FragmentTransaction ftr = fm.beginTransaction();
        DialogFragment fragmentDialog = null;
        fragmentDialog = (DialogFragment) fm.findFragmentByTag("" + pDialogId);
        if (fragmentDialog != null) {
            FragmentTransaction f = ftr.remove(fragmentDialog);
            f.commit();
        }
    }

    protected static class CustomDialogFragment extends DialogFragment {
        private Dialog mDialog;

        public static CustomDialogFragment newInstance(Dialog dialog) {
            CustomDialogFragment frag = new CustomDialogFragment();
            frag.mDialog = dialog;
            return frag;
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    public class ListAdapter extends BaseAdapter {
        private LayoutInflater mInflater;
        private List<Voltage> results;

        public ListAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return results.size();
        }

        @Override
        public Object getItem(int position) {
            return results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {

            final ViewHolder holder;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.list_volt, null);
                holder = new ViewHolder();
                holder.mFreq = (TextView) convertView.findViewById(R.id.Freq);
                holder.mCurrentMV = (TextView) convertView.findViewById(R.id.mVCurrent);
                holder.mSavedMV = (TextView) convertView.findViewById(R.id.mVSaved);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final Voltage voltage = mVoltages.get(position);
            holder.setFreq(voltage.getFreq());
            holder.setCurrentMV(voltage.getCurrentMv());
            holder.setSavedMV(voltage.getSavedMV());
            return convertView;
        }

        public void setListItems(List<Voltage> mVoltages) {
            results = mVoltages;
        }

        public class ViewHolder {
            private TextView mFreq;
            private TextView mCurrentMV;
            private TextView mSavedMV;

            public void setFreq(final String freq) {
                mFreq.setText(freq);
            }

            public void setCurrentMV(final String currentMv) {
               mCurrentMV.setText(getResources().getString(R.string.ps_volt_current_voltage) + currentMv );
            }

            public void setSavedMV(final String savedMv) {
               mSavedMV.setText(getResources().getString(R.string.ps_volt_setting_to_apply) + savedMv );
            }
        }
    }
}

