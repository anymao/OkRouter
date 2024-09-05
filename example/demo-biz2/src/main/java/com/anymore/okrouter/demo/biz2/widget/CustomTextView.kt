package com.anymore.okrouter.demo.biz2.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.demo.base.Common

@Router(
    scheme = Common.scheme,
    host = Common.host,
    path = "/custom_text_view",
    desc = "自定义View"
)
class CustomTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        text = "自定义TextView"
    }
}