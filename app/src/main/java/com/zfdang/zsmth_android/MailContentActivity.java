package com.zfdang.zsmth_android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jude.swipbackhelper.SwipeBackHelper;
import com.klinker.android.link_builder.Link;
import com.klinker.android.link_builder.LinkBuilder;
import com.klinker.android.link_builder.LinkConsumableTextView;
import com.zfdang.SMTHApplication;
import com.zfdang.zsmth_android.fresco.WrapContentDraweeView;
import com.zfdang.zsmth_android.helpers.Regex;
import com.zfdang.zsmth_android.models.ComposePostContext;
import com.zfdang.zsmth_android.models.ContentSegment;
import com.zfdang.zsmth_android.models.Mail;
import com.zfdang.zsmth_android.models.Post;
import com.zfdang.zsmth_android.newsmth.AjaxResponse;
import com.zfdang.zsmth_android.newsmth.SMTHHelper;

import java.util.ArrayList;
import java.util.List;

import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MailContentActivity extends AppCompatActivity {

    private static final String TAG = "MailContent";
    private Mail mMail;
    private Post mPost;

    public TextView mPostAuthor;
    public TextView mPostIndex;
    public TextView mPostPublishDate;
    private LinearLayout mViewGroup;
    public LinkConsumableTextView mPostContent;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SwipeBackHelper.onDestroy(this);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        SwipeBackHelper.onPostCreate(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SwipeBackHelper.onCreate(this);

        setContentView(R.layout.activity_mail_content);

        // init post widget
        mPostAuthor = (TextView) findViewById(R.id.post_author);
        mPostIndex = (TextView) findViewById(R.id.post_index);
        mPostIndex.setVisibility(View.GONE);
        mPostPublishDate = (TextView) findViewById(R.id.post_publish_date);
        mViewGroup = (LinearLayout) findViewById(R.id.post_content_holder);
        mPostContent = (LinkConsumableTextView) findViewById(R.id.post_content);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Show the Up button in the action bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // load mMail content
        Bundle bundle = getIntent().getExtras();
        mMail = (Mail) bundle.getParcelable(SMTHApplication.MAIL_OBJECT);
        loadMailContent();
    }

    public void loadMailContent() {
        SMTHHelper helper = SMTHHelper.getInstance();
        helper.wService.getMailContent(mMail.url)
                .map(new Func1<AjaxResponse, Post>() {
                    @Override
                    public Post call(AjaxResponse ajaxResponse) {
                        return SMTHHelper.ParseMailContentFromWWW(ajaxResponse.getContent());
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Post>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        mPostContent.setText("读取内容失败: \n" + e.toString());
                    }

                    @Override
                    public void onNext(Post post) {
                        mPost = post;

                        // copy some attr from mail to post
                        mPost.setAuthor(mMail.getFrom());
                        mPost.setTitle(mMail.title);
                        mPost.setPostID(mMail.getMailIDFromURL());

                        mPostAuthor.setText(mPost.getRawAuthor());
                        mPostPublishDate.setText(mPost.getFormatedDate());
                        inflateContentViewGroup(mViewGroup, mPostContent, mPost);
                    }
                });

    }

    //    copied from PostRecyclerViewAdapter.inflateContentViewGroup, almost the same code
    public void inflateContentViewGroup(ViewGroup viewGroup, TextView contentView, final Post post) {
        // remove all child view in viewgroup
        viewGroup.removeAllViews();

        List<ContentSegment> contents = post.getContentSegments();
        if(contents == null) return;

        if(contents.size() > 0) {
            // there are multiple segments, add the first contentView first
            // contentView is always available, we don't have to inflate it again
            ContentSegment content = contents.get(0);
            contentView.setText(content.getSpanned());
            LinkBuilder.on(contentView).addLinks(getPostSupportedLinks()).build();

            viewGroup.addView(contentView);
        }


        // http://stackoverflow.com/questions/13438473/clicking-html-link-in-textview-fires-weird-androidruntimeexception
        final LayoutInflater inflater = getLayoutInflater();
        for(int i = 1; i < contents.size(); i++) {
            ContentSegment content = contents.get(i);

            if(content.getType() == ContentSegment.SEGMENT_IMAGE) {
                // Log.d("CreateView", "Image: " + content.getUrl());

                // Add the text layout to the parent layout
                WrapContentDraweeView image = (WrapContentDraweeView) inflater.inflate(R.layout.post_item_imageview, viewGroup, false);
                image.setImageFromStringURL(content.getUrl());

                // set onclicklistener
                image.setTag(R.id.image_tag, content.getImgIndex());

                // Add the text view to the parent layout
                viewGroup.addView(image);
            } else if (content.getType() == ContentSegment.SEGMENT_TEXT) {
                // Log.d("CreateView", "Text: " + content.getSpanned().toString());

                // Add the links and make the links clickable
                LinkConsumableTextView tv = (LinkConsumableTextView) inflater.inflate(R.layout.post_item_content, viewGroup, false);
                tv.setText(content.getSpanned());
                LinkBuilder.on(tv).addLinks(getPostSupportedLinks()).build();

                // Add the text view to the parent layout
                viewGroup.addView(tv);
            }
        }

    }

    private List<Link> getPostSupportedLinks() {
        List<Link> links = new ArrayList<>();

        // web URL link
        Link weburl = new Link(Regex.WEB_URL_PATTERN);
        weburl.setTextColor(Color.parseColor("#00BCD4"));
        weburl.setHighlightAlpha(.4f);
        weburl.setOnClickListener(new Link.OnClickListener() {
            @Override
            public void onClick(String clickedText) {
                openLink(clickedText);
            }
        });
        weburl.setOnLongClickListener(new Link.OnLongClickListener() {
            @Override
            public void onLongClick(String clickedText) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    final android.content.ClipboardManager clipboardManager = (android.content.ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    final android.content.ClipData clipData = android.content.ClipData.newPlainText("PostContent", clickedText);
                    clipboardManager.setPrimaryClip(clipData);
                } else {
                    final android.text.ClipboardManager clipboardManager = (android.text.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboardManager.setText(clickedText);
                }
                Toast.makeText(SMTHApplication.getAppContext(), "链接已复制到剪贴板", Toast.LENGTH_SHORT).show();
            }
        });

        // email link
        Link emaillink = new Link(Regex.EMAIL_ADDRESS_PATTERN);
        emaillink.setTextColor(Color.parseColor("#00BCD4"));
        emaillink.setHighlightAlpha(.4f);
        emaillink.setOnClickListener(new Link.OnClickListener() {
            @Override
            public void onClick(String clickedText) {
                sendEmail(clickedText);
            }
        });

        links.add(weburl);
        links.add(emaillink);

        return links;
    }

    private void openLink(String link) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        startActivity(browserIntent);
    }


    private void sendEmail(String link) {
        /* Create the Intent */
        final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);

        /* Fill it with Data */
        emailIntent.setType("plain/text");
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{link});
        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "来自zSMTH的邮件");
        emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, "");

        /* Send it off to the Activity-Chooser */
        startActivity(Intent.createChooser(emailIntent, "发邮件..."));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.mail_content_reply) {
            if(mPost == null) {
                Toast.makeText(MailContentActivity.this, "帖子内容错误，无法回复！", Toast.LENGTH_LONG).show();
                return true;
            } else {
                ComposePostContext postContext = new ComposePostContext();
                postContext.setPostid(mPost.getPostID());
                postContext.setPostTitle(mPost.getTitle());
                postContext.setPostAuthor(mPost.getRawAuthor());
                postContext.setPostContent(mPost.getRawContent());

                if(mMail.fromBoard != null && mMail.fromBoard.length() > 0) {
                    postContext.setBoardEngName(mMail.fromBoard);
                    postContext.setThroughMail(false);
                } else {
                    postContext.setThroughMail(true);
                }

                Intent intent = new Intent(this, ComposePostActivity.class);
                intent.putExtra(SMTHApplication.COMPOSE_POST_CONTEXT, postContext);
                startActivity(intent);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.mail_content_menu, menu);
        return true;
    }

}
