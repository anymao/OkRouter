package com.anymore.okrouter.demo.biz1

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.anymore.okrouter.OkRouter
import com.anymore.okrouter.annotation.Router
import com.anymore.okrouter.demo.base.Common
import com.anymore.okrouter.demo.base.Common.toRoute

@Router(scheme = Common.scheme, host = Common.host, path = "/biz1_view_page", desc = "Biz1 View Page")
class Biz1ViewPageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_biz1_viewpage)
        val fragments = mutableListOf<Fragment>()
        val f1 = OkRouter.build("/fragment1".toRoute()).start(this).target as? Fragment
        if (f1 != null) {
            fragments.add(f1)
        }
        val f2 = OkRouter.build("/fragment2".toRoute()).putString("data", "传递数据").start(this).target as? Fragment
        if (f2 != null) {
            fragments.add(f2)
        }
        val f3 = OkRouter.build("/fragment3".toRoute()).start(this).target as? Fragment
        if (f3 != null) {
            fragments.add(f3)
        }
        val vp = findViewById<ViewPager2>(R.id.view_pager)
        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size

            override fun createFragment(index: Int): Fragment {
                return fragments[index]
            }
        }
        vp.adapter = adapter
    }
}