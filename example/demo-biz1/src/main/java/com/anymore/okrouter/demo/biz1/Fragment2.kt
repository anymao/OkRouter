package com.anymore.okrouter.demo.biz1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.demo.base.Common

@Router(scheme = Common.scheme, host = Common.host, path = "/fragment2", desc = "Fragment2")
class Fragment2 : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val data = arguments?.getString("data")
        if (!data.isNullOrBlank()){
            view.findViewById<TextView>(R.id.text)?.text = data
        }
    }

}