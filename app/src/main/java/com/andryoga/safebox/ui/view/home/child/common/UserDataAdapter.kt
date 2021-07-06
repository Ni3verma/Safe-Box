package com.andryoga.safebox.ui.view.home.child.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andryoga.safebox.databinding.RowHomeInfoItemBinding

class UserDataAdapter(private val clickListener: UserDataClickListener) :
    ListAdapter<UserDataAdapterEntity, UserDataAdapter.UserDataViewHolder>(UserDataDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserDataViewHolder {
        return UserDataViewHolder.from(
            parent
        )
    }

    override fun onBindViewHolder(holder: UserDataViewHolder, position: Int) {
        holder.bind(getItem(position)!!, clickListener)
    }

    class UserDataViewHolder private constructor(val binding: RowHomeInfoItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): UserDataViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RowHomeInfoItemBinding.inflate(layoutInflater, parent, false)
                return UserDataViewHolder(
                    binding
                )
            }
        }

        fun bind(item: UserDataAdapterEntity, clickListener: UserDataClickListener) {
            binding.data = item
            binding.clickListener = clickListener
            binding.executePendingBindings()
        }
    }
}

class UserDataClickListener(val clickListener: (userDataAdapterEntity: UserDataAdapterEntity) -> Unit) {
    fun onClick(userDataAdapterEntity: UserDataAdapterEntity) = clickListener(userDataAdapterEntity)
}
