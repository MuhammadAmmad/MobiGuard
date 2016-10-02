package com.callsms.calls;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.fs.lib.util.BaseAlertDialog;
import com.fs.lib.util.BaseFragment;
import com.fs.lib.util.MyEventListener;
import com.fs.lib.util.MyLogger;
import com.fs.lib.util.OnConfirmDialog;
import com.mobiguard.MyApp;
import com.mobiguard.OnActionBarTitleChange;
import com.mobiguard.OnSetDrawerEnabledEvent;
import com.mobiguard.R;
import com.util.MyDatabaseHelper;
import com.j256.ormlite.stmt.DeleteBuilder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.greenrobot.event.EventBus;

public  class BlockedCallsFragment extends BaseFragment implements MyEventListener {
    @Bind(R.id.actionbar_selected_items_count)
    TextView actionbar_selected_items_count;

    @Bind(R.id.my_recycler_view)
    RecyclerView mRecyclerView;

    @Bind(R.id.textViewEmpty)
    TextView textViewEmpty;

    @Bind(R.id.menu_action_save)
    ImageView menu_action_save;

    @Bind(R.id.menu_action_clear)
    ImageView menu_action_clear;

    @Bind(R.id.bottomMenu)
    Toolbar bottomMenu;

    private List<BlockedCalls> items = new ArrayList<>();
    private MyDatabaseHelper db;
    private BlockedCallsAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private FragmentManager fragmentManager;
    private BaseAlertDialog chooseNumberClickOptions;
    private BlockedCalls blockedCalls;
    private boolean isCabActive=false;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_blocked_call_list, null);
        ButterKnife.bind(this, view);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        db = MyDatabaseHelper.getInstance(MyApp.getContext());
        initOnClickAlertDialog();
        loadItems();
        bottomMenu.setVisibility(View.GONE);
        return view;
    }

    public void initOnClickAlertDialog() {
        CharSequence items[] = new CharSequence[]{"Call","SMS","Delete"};

        chooseNumberClickOptions = new BaseAlertDialog(getContext(), R.string.dialog_title_blockList_call_item, items) {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: // Call
                        callNumber();
                        break;
                    case 1: // SMS
                        SMSNumber();
                        break;
                    case 2:  // Delete
                        deleteNumber(blockedCalls);
                        loadItems();
                        break;
                }
            }
        };
    }

    private void SMSNumber() {
        Intent smsIntent = new Intent(Intent.ACTION_VIEW);
        smsIntent.setType("vnd.android-dir/mms-sms");
        smsIntent.putExtra("address", blockedCalls.getBlockedNumber());
        smsIntent.putExtra("sms_body", "");
        startActivity(smsIntent);
    }

    private void callNumber() {
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + blockedCalls.getBlockedNumber()));
        startActivity(intent);
    }
    private void deleteNumber(BlockedCalls m) {
        try {
            DeleteBuilder<BlockedCalls, Long> dao=  db.getBlockedCallDao().deleteBuilder();
            dao.where().eq(MyDatabaseHelper.id, m.getId());
            dao.delete();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    public void onEvent(OnRefreshBlockedNumberList event){
        loadItems();
    }


    private void loadItems() {
        try {
            items = db.getAllCallBlocked();
            MyLogger.debug("Call logs count " + items.size());
            if (items.size() > 0) {
                textViewEmpty.setVisibility(View.GONE);
            }else {
                textViewEmpty.setVisibility(View.VISIBLE);
            }
            mAdapter = new BlockedCallsAdapter(items, R.layout.list_item_blocked_call);
            mAdapter.setMyEventListener(this);
            mRecyclerView.setAdapter(mAdapter);

        } catch (Exception e) {
            MyLogger.debug(e.getMessage());
            e.printStackTrace();
        }
    }


    @Override
    public void backPressedInFragment() {
        EventBus.getDefault().post(new OnSetDrawerEnabledEvent(true));
    }

    @Override
    public void onItemClick(int position, View v) {
        if(!isCabActive){
            MyLogger.debug("onItemClick in  "+getClass().getName());
            blockedCalls = mAdapter.items.get(position);
            chooseNumberClickOptions.showDialog();
            // show Alert Dialog
        }else {
            BlockedCalls item=mAdapter.items.get(position);
            mAdapter.toggleSelection(position);
            actionbar_selected_items_count.setText(String.valueOf(mAdapter.selectedItems.size()));
            if (mAdapter.selectedItems.size()==0){
                bottomMenu.setVisibility(View.GONE);
                EventBus.getDefault().post(new OnActionBarTitleChange("Blocked Calls"));
                isCabActive=false;
            }
        }

    }
    @OnClick(R.id.menu_action_save)
    public void menuActionSave(View view) {
        MyLogger.debug("Saving Add Image to Vault");
        OnConfirmDialog dialog=   new OnConfirmDialog(getContext(),"Do you want to delete?"){

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        for ( Map.Entry<Integer, Boolean> item :mAdapter.selectedItems.entrySet()) {
                            BlockedCalls m= mAdapter.items.get(item.getKey());
                            deleteNumber(m);
                        }
                        mAdapter.clearSelections();
                        bottomMenu.setVisibility(View.GONE);
                        EventBus.getDefault().post(new OnActionBarTitleChange("Blocked Calls"));
                        isCabActive=false;
                        loadItems();
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        dialog.cancel();
                        break;
                }
            }
        };
        dialog.show();
    }

    @OnClick(R.id.menu_action_clear)
    public void menuActionClear(View view) {
        MyLogger.debug("Clearning Add Image to Vault");
        mAdapter.clearSelections();
        actionbar_selected_items_count.setText(String.valueOf(0));
    }
    @Override
    public void onItemLongClick(int position, View v) {
        isCabActive=true;
        BlockedCalls item=mAdapter.items.get(position);
        mAdapter.toggleSelection(position);
        actionbar_selected_items_count.setText(String.valueOf(mAdapter.selectedItems.size()));
        bottomMenu.setVisibility(View.VISIBLE);
        EventBus.getDefault().post(new OnActionBarTitleChange("Delete Call Log"));

    }

    @Override
    public void onItemCheckedChange(int position,CompoundButton button, boolean checked) {

    }
}