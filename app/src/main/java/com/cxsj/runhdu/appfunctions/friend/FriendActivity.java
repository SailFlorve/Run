package com.cxsj.runhdu.appfunctions.friend;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.cxsj.runhdu.R;
import com.cxsj.runhdu.adapters.FriendRecyclerViewAdapter;
import com.cxsj.runhdu.base.BaseActivity;
import com.cxsj.runhdu.base.BaseModel;
import com.cxsj.runhdu.bean.gson.FriendInfo;
import com.cxsj.runhdu.bean.gson.MyFriend;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * 好友
 */
public class FriendActivity extends BaseActivity {
    private RecyclerView friendListView;
    private FriendRecyclerViewAdapter adapter;
    private FloatingActionButton addFriendButton;
    private SwipeRefreshLayout refreshLayout;

    private LinearLayout noFriendTipLayout;
    private RelativeLayout haveApplyLayout;
    private TextView applyText;

    private List<FriendInfo> friendInfoList = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend);
        setToolbar(R.id.toolbar_friend, true);
        initView();
        //从存储的json里初始化好友列表
        initFriendData();
        //获取最新好友列表
        getFriendData();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        getFriendData();
        super.onNewIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.friend_activity_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.friend_refresh:
                getFriendData();
                break;
            case R.id.friend_box:
                toActivity(this, FriendApplyBoxActivity.class);
                break;
            default:
        }
        return super.onOptionsItemSelected(item);
    }

    private void initView() {
        friendListView = (RecyclerView) findViewById(R.id.friend_list);
        addFriendButton = (FloatingActionButton) findViewById(R.id.add_friend);
        refreshLayout = (SwipeRefreshLayout) findViewById(R.id.friend_refresh_layout);
        noFriendTipLayout = (LinearLayout) findViewById(R.id.no_friend_tip_layout);
        haveApplyLayout = (RelativeLayout) findViewById(R.id.have_apply_layout);
        applyText = (TextView) findViewById(R.id.have_apply_text);
        refreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.colorAccent));

        adapter = new FriendRecyclerViewAdapter(
                R.layout.friend_list_item, friendInfoList);
        adapter.openLoadAnimation();
        GridLayoutManager manager = new GridLayoutManager(this, 1);
        friendListView.setAdapter(adapter);
        friendListView.setLayoutManager(manager);

        haveApplyLayout.setOnClickListener(v ->
                toActivity(FriendActivity.this, FriendApplyBoxActivity.class));

        adapter.setOnItemClickListener((adapter, view, position) -> {
            Intent intent = new Intent(FriendActivity.this, FriendDetailsActivity.class);
            intent.putExtra("friend_user_name", friendInfoList.get(position).getUsername());
            ActivityOptionsCompat options = ActivityOptionsCompat.
                    makeSceneTransitionAnimation(this,
                            view.findViewById(R.id.friend_profile_list_item),
                            "friend_profile");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                startActivity(intent, options.toBundle());
            } else {
                startActivity(intent);
            }
        });

        addFriendButton.setOnClickListener(v -> {
            final View viewDialog = getLayoutInflater().inflate(R.layout.add_friend_dialog, null);
            new AlertDialog.Builder(
                    FriendActivity.this)
                    .setTitle("添加好友")
                    .setView(viewDialog)
                    .setPositiveButton("确定", (dialog, which) -> {
                        TextInputLayout inputLayout = (TextInputLayout)
                                viewDialog.findViewById(R.id.friend_username_input_layout);
                        String usernameInput = inputLayout
                                .getEditText().getText().toString().trim();
                        if (username.equals(usernameInput)) {
                            showSnackBar("不能添加自己哦。", "重试", v1 ->
                                    addFriendButton.callOnClick());
                        } else {
                            applyFriend(usernameInput);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .create().show();
        });

        refreshLayout.setOnRefreshListener(this::getFriendData);
    }

    /**
     * 从缓存里加载好友列表
     */
    private void initFriendData() {
        String friendJson = (String) prefs.get(username + "_friend_json", "");
        if (!TextUtils.isEmpty(friendJson)) {
            MyFriend myFriend = new Gson().fromJson(friendJson, MyFriend.class);
            setFriendView(myFriend);
        }
    }

    /**
     * 从服务器获取最新好友列表
     */
    private void getFriendData() {
        refreshLayout.setRefreshing(true);
        FriendModel.getFriendList(username, new FriendModel.GetFriendCallback() {
            @Override
            public void onSuccess(String json, MyFriend myFriend) {
                refreshLayout.setRefreshing(false);
                prefs.put(username + "_friend_json", json);
                setFriendView(myFriend);
                if (!myFriend.getApplyList().isEmpty()) {
                    haveApplyLayout.setVisibility(View.VISIBLE);
                    applyText.setText(String.format("你有%d条好友请求。",
                            myFriend.getApplyList().size()));
                } else {
                    haveApplyLayout.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(String msg) {
                refreshLayout.setRefreshing(false);
                showSnackBar(msg, "重试", v -> getFriendData());
            }
        });
    }

    /**
     * 设置好友列表
     *
     * @param myFriend
     */
    private void setFriendView(MyFriend myFriend) {
        friendInfoList.clear();
        if (myFriend.getFriendList().isEmpty()) {
            noFriendTipLayout.setVisibility(View.VISIBLE);
        } else {
            noFriendTipLayout.setVisibility(View.GONE);
            friendInfoList.addAll(myFriend.getFriendList());
        }
        adapter.notifyDataSetChanged();
    }

    private void applyFriend(String friendName) {
        if (TextUtils.isEmpty(friendName)) {
            showSnackBar("好友用户名为空！", "重试", v ->
                    addFriendButton.callOnClick());
            return;
        }
        showProgressDialog("正在申请好友...");
        FriendModel.addFriend(username, friendName, new BaseModel.BaseCallback() {
            @Override
            public void onFailure(String msg) {
                closeProgressDialog();
                showSnackBar(msg, "重试", v ->
                        addFriendButton.callOnClick());
            }

            @Override
            public void onSuccess() {
                closeProgressDialog();
                showSnackBar("请求发送成功。");
            }
        });
    }

    private void showSnackBar(String text) {
        Snackbar.make(refreshLayout, text, Snackbar.LENGTH_LONG)
                .setAction("知道了", v -> {
                }).show();
    }

    private void showSnackBar(String text, String actionName, View.OnClickListener listener) {
        Snackbar.make(refreshLayout, text, Snackbar.LENGTH_LONG)
                .setAction(actionName, listener).show();
    }
}
