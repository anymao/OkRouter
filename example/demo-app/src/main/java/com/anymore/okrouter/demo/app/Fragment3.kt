package com.anymore.okrouter.demo.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.demo.base.Common

@Router(scheme = Common.scheme, host = Common.host, path = "/fragment3", desc = "Fragment3")
class Fragment3 : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_3, container, false)
    }

}