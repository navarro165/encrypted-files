package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemFileBinding
import java.io.File

class FileAdapter(
    private val onMultiSelect: (List<File>) -> Unit,
    private val onOpenFile: (File) -> Unit,
    private val onOpenFolder: (File) -> Unit
) : ListAdapter<File, FileAdapter.ViewHolder>(FileDiffCallback()) {

    private var isMultiSelectMode = false
    private val selectedItems = mutableListOf<File>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = getItem(position)
        holder.bind(file)
    }

    fun setMultiSelectMode(enabled: Boolean) {
        isMultiSelectMode = enabled
        if (!enabled) {
            selectedItems.clear()
        }
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<File> {
        return selectedItems
    }

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(currentList)
        notifyDataSetChanged()
        onMultiSelect(selectedItems)
    }

    inner class ViewHolder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: File) {
            binding.textView.text = file.name

            if (file.isDirectory) {
                binding.imageView.visibility = View.VISIBLE
                binding.imageView.setImageResource(android.R.drawable.ic_menu_gallery) // Placeholder for folder icon
            } else {
                binding.imageView.visibility = View.GONE
            }

            if (isMultiSelectMode) {
                binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = selectedItems.contains(file)
            } else {
                binding.checkbox.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                if (isMultiSelectMode) {
                    if (selectedItems.contains(file)) {
                        selectedItems.remove(file)
                    } else {
                        selectedItems.add(file)
                    }
                    notifyItemChanged(adapterPosition)
                    onMultiSelect(selectedItems)
                } else {
                    if (file.isDirectory) {
                        onOpenFolder(file)
                    } else {
                        onOpenFile(file)
                    }
                }
            }

            binding.root.setOnLongClickListener {
                if (!isMultiSelectMode) {
                    setMultiSelectMode(true)
                    selectedItems.add(file)
                    notifyItemChanged(adapterPosition)
                    onMultiSelect(selectedItems)
                }
                true
            }
        }
    }
}

class FileDiffCallback : DiffUtil.ItemCallback<File>() {
    override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
        return oldItem == newItem
    }
}