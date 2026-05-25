package com.yukissh

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class ConnectionListActivity : AppCompatActivity() {

    private lateinit var homeFragment: HomeFragment
    private lateinit var aboutFragment: AboutFragment
    private lateinit var fabAdd: ExtendedFloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection_list)

        homeFragment = HomeFragment()
        aboutFragment = AboutFragment()
        fabAdd = findViewById(R.id.fabAdd)

        // Show home by default
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, aboutFragment, "about").hide(aboutFragment)
            .add(R.id.fragmentContainer, homeFragment, "home")
            .commit()

        fabAdd.setOnClickListener {
            startActivity(Intent(this, EditConnectionActivity::class.java))
        }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showFragment(homeFragment)
                    fabAdd.show()
                    true
                }
                R.id.nav_about -> {
                    showFragment(aboutFragment)
                    fabAdd.hide()
                    true
                }
                else -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment) {
        val ft = supportFragmentManager.beginTransaction()
        if (fragment === homeFragment) {
            ft.hide(aboutFragment).show(homeFragment)
        } else {
            ft.hide(homeFragment).show(aboutFragment)
        }
        ft.commit()
    }
}
