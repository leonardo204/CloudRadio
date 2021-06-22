package com.zerolive.cloudradio

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*

data class ListViewItem (
    var iconFavorite: Drawable? = null,
    var type: String,
    var iconType: Drawable,
    var defaultText: String,
    var iconDelete: Drawable? = null
)

class ListViewAdaptor : BaseAdapter(), View.OnClickListener {
    private var listViewItemList = ArrayList<ListViewItem>()
    private var onClickListener: ListBtnClickListener? = null

    // 버튼 클릭 이벤트를 위한 Listener 인터페이스 정의.
    interface ListBtnClickListener {
        fun onListBtnClick(position: Int, view: View?)
    }

    fun setListener(listener: ListBtnClickListener) {
        onClickListener = listener
    }

    override fun onClick(v: View?) {
        onClickListener?.let {
            it.onListBtnClick(v?.getTag() as Int, v)
        }
    }

    override fun getCount(): Int {
        return listViewItemList.size
    }

    override fun getItem(position: Int): Any {
        return listViewItemList.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun getItem(title: String): ListViewItem? {
        for ( i in listViewItemList.indices ) {
            if ( listViewItemList.get(i).defaultText.contains(title) ) {
                return listViewItemList.get(i)
            }
        }
        return null
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = convertView
        val context = parent?.context

        if ( view == null ) {
            val inflater = context?.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(R.layout.listview_items, parent, false)
        }

        val iconFavorite = view!!.findViewById(R.id.fav_item_checked) as ImageView
        val iconType = view!!.findViewById(R.id.fav_item_icon) as ImageView
        val title = view.findViewById(R.id.fav_item_btn) as TextView
        val iconDelete = view.findViewById(R.id.fav_item_delete) as ImageButton

        val listViewItem = listViewItemList[position]

        iconFavorite.setImageDrawable(listViewItem.iconFavorite)
        iconType.setImageDrawable(listViewItem.iconType)
        title.setText(listViewItem.defaultText)
        iconDelete.setImageDrawable(listViewItem.iconDelete)

        iconDelete.setTag( position )
        iconDelete.setOnClickListener(this)

        return view
    }

    fun addItem(item: ListViewItem) {
        CRLog.d("Adaptor addItem (Cur: ${count}) type:${item.type}   title:${item.defaultText}    delete: ${item.iconDelete}")
        listViewItemList.add(item)
        listViewItemList.sortBy { item -> item.type }
    }

    fun removeItem(title: String) {
        CRLog.d("Adaptor removeItem (Cur: ${count}) title:${title}")

        for ( i in listViewItemList.indices ) {
            val targetTitle = listViewItemList.get(i).defaultText
            if ( targetTitle.equals(title) ) {
                listViewItemList.removeAt(i)
                CRLog.d("Adaptor removeItem  success ! (Cur: ${count}) title:${title}")
                break
            }
        }
    }

    fun removeAll() {
        CRLog.d("Adaptor removeAll (Cur: ${count})")
        listViewItemList.clear()
        CRLog.d("Adaptor removeAll  success ! (Cur: ${count})")
    }
}