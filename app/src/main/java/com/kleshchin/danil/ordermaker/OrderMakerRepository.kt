package com.kleshchin.danil.ordermaker

import android.content.Context
import android.database.Cursor
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.LoaderManager
import android.support.v4.content.CursorLoader
import android.support.v4.content.Loader
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.kleshchin.danil.ordermaker.models.CategoryMeal
import com.kleshchin.danil.ordermaker.models.Meal
import com.kleshchin.danil.ordermaker.provider.DatabaseHelper
import com.kleshchin.danil.ordermaker.provider.OrderMakerProvider
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.lang.ref.WeakReference
import android.os.AsyncTask.execute
import org.json.JSONObject


object OrderMakerRepository : LoaderManager.LoaderCallbacks<Cursor> {

    private val CATEGORY_CODE = 0
    private val MEAL_CODE = 1
    private val TAG = "OrderMakerRepository"
    private val OKHTTP_TAG = "OrderMakerRepository"
    private val SERVER_ADDRESS = "http://192.168.0.102:3000"

    private var categoryListener: OnReceiveCategoryInformationListener? = null
    private var mealListener: OnReceiveMealInformationListener? = null
    private var context: WeakReference<Context>? = null

    interface OnReceiveCategoryInformationListener {
        fun onCategoryReceive(categoryList: ArrayList<CategoryMeal>?)
    }

    interface OnReceiveMealInformationListener {
        fun onMealReceive(mealList: ArrayList<Meal>?)
    }

    fun setOnReceiveMealInformationListener(context: Context, listener: OnReceiveMealInformationListener) {
        this.context = WeakReference(context)
        mealListener = listener
    }

    fun setOnReceiveCategoryInformationListener(context: Context, listener: OnReceiveCategoryInformationListener) {
        this.context = WeakReference(context)
        categoryListener = listener
    }

    fun loadCategory() {
        val categoryLoader = InfoDownloader(InfoDownloader.Models.Category)
        categoryLoader.execute()
    }

    fun loadMeal(categoryId: Int) {
        val loader = InfoDownloader(InfoDownloader.Models.Meal, categoryId)
        loader.execute()
    }

    fun sendMeal(meal: Meal) {
        Thread {
            val client = OkHttpClient()
            val requestBody = FormBody.Builder()
                    .add("categoryId", meal.categoryId.toString())
                    .add("name", meal.name)
                    .add("price", meal.price.toString())
                    .add("imageUrl", meal.iconUrl)
                    .add("description", meal.info)
                    .build()
            val request = Request.Builder()
                    .url(SERVER_ADDRESS + "/order")
                    .post(requestBody)
                    .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful()) {
                throw IOException("Unexpected code $response")
            }
        }.start()

    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        when (id) {
            CATEGORY_CODE -> return CursorLoader(context?.get(), OrderMakerProvider.createUrlForTable(DatabaseHelper.CATEGORY_TABLE), null, null, null, null)
            MEAL_CODE -> return CursorLoader(context?.get(), OrderMakerProvider.createUrlForTable(DatabaseHelper.MEAL_TABLE), null, null, null, null)
            else -> throw IllegalArgumentException("no id handed")
        }
    }

    override fun onLoadFinished(loader: Loader<Cursor>?, data: Cursor?) {
        val loaderId = loader!!.id
        when (loaderId) {
            CATEGORY_CODE -> {
                categoryListener!!.onCategoryReceive(DatabaseHelper.createCategoryFromCursor(data!!))
            }
            MEAL_CODE -> {
                mealListener!!.onMealReceive(DatabaseHelper.createMealFromCursor(data!!))
            }
            else -> throw IllegalArgumentException("no loader id handled!")
        }
        data.close()
        (context?.get() as AppCompatActivity).loaderManager.destroyLoader(loaderId)
    }

    override fun onLoaderReset(loader: Loader<Cursor>?) {

    }

    private class InfoDownloader(var model: Models, var id: Int = -1) : AsyncTask<Void, Void, Void>() {
        enum class Models {
            Meal, Category
        }

        override fun doInBackground(vararg p0: Void?): Void? {
            when (model) {
                Models.Category -> {
                    loadCategory()
                }
                Models.Meal -> {
                    loadMeal(id)
                }
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            var tableCode: Int = -1
            when (model) {
                Models.Meal -> {
                    tableCode = MEAL_CODE
                }
                Models.Category -> {
                    tableCode = CATEGORY_CODE
                }
            }
            (context?.get() as AppCompatActivity).supportLoaderManager.restartLoader(tableCode, null, OrderMakerRepository)
            super.onPostExecute(result)
        }

        private fun loadCategory() {
            try {
                val url = SERVER_ADDRESS + "/category"
                val client = OkHttpClient()
                val request = Request.Builder()
                        .url(url)
                        .build()
                var responses: Response? = null
                try {
                    responses = client.newCall(request).execute()
                    Log.i(OKHTTP_TAG, "Load url " + url)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                val categoryList: ArrayList<CategoryMeal> = ArrayList()
                val jsonData = responses?.body()?.string()
                Log.i(OKHTTP_TAG, jsonData)
                val jsonArray = JSONArray(jsonData)
                for (i in 0..(jsonArray.length() - 1)) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val name = jsonObject.getString("name")
                    val imageUrl = SERVER_ADDRESS + jsonObject.getString("imageUrl")
                    val id = jsonObject.getInt("id")
                    categoryList.add(CategoryMeal(id, name, imageUrl))
                }
                val resolver = context?.get()?.contentResolver
                if (resolver != null) {
                    DatabaseHelper.insertCategoryList(categoryList, resolver)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        private fun loadMeal(categoryId: Int) {
            try {
                val url = SERVER_ADDRESS + "/meal"
                val client = OkHttpClient()
                val request = Request.Builder()
                        .url(url)
                        .build()
                var responses: Response? = null
                try {
                    responses = client.newCall(request).execute()
                    Log.i(OKHTTP_TAG, "Load url " + url)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                val mealList: ArrayList<Meal> = ArrayList()
                val jsonData = responses?.body()?.string()
                Log.i(OKHTTP_TAG, jsonData)
                val jsonArray = JSONArray(jsonData)
                for (i in 0..(jsonArray.length() - 1)) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    if (jsonObject.getInt("categoryId") == categoryId) {
                        val name = jsonObject.getString("name")
                        val imageUrl = SERVER_ADDRESS + jsonObject.getString("imageUrl")
                        val price = jsonObject.getInt("price")
                        val description = jsonObject.getString("description")
                        mealList.add(Meal(categoryId, name, imageUrl, price, false, description))
                    }
                }
                val resolver = context?.get()?.contentResolver
                if (resolver != null) {
                    DatabaseHelper.insertMealList(mealList, resolver)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }
}
