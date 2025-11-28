package com.example.Photo

import android.graphics.Matrix

/**
 * 图片编辑历史栈，管理Undo/Redo操作
 */
class EditHistoryStack {
    // 撤销栈：保存每次操作前的矩阵状态
    private val undoStack = ArrayDeque<Matrix>()
    // 恢复栈：保存撤销后的矩阵状态
    private val redoStack = ArrayDeque<Matrix>()

    /**
     * 保存当前编辑状态（执行新操作前调用）
     */
    fun saveState(matrix: Matrix) {
        // 深拷贝矩阵，避免引用传递导致状态错乱
        val copyMatrix = Matrix(matrix)
        undoStack.addLast(copyMatrix) // 替换offerLast为addLast
        // 新操作后清空恢复栈
        redoStack.clear()
    }

    /**
     * 撤销操作
     * @return 上一次的矩阵状态，无则返回null
     */
    fun undo(): Matrix? {
        if (undoStack.isEmpty()) return null
        val lastState = undoStack.removeLast() // 替换pollLast为removeLast
        redoStack.addLast(Matrix(lastState)) // 替换offerLast为addLast
        return if (undoStack.isEmpty()) null else undoStack.last()
    }

    /**
     * 恢复操作
     * @return 恢复后的矩阵状态，无则返回null
     */
    fun redo(): Matrix? {
        if (redoStack.isEmpty()) return null
        val nextState = redoStack.removeLast() // 替换pollLast为removeLast
        undoStack.addLast(Matrix(nextState)) // 替换offerLast为addLast
        return nextState
    }

    /**
     * 清空历史栈（重置编辑状态时调用）
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    /**
     * 判断是否可撤销
     */
    fun canUndo() = undoStack.isNotEmpty()

    /**
     * 判断是否可恢复
     */
    fun canRedo() = redoStack.isNotEmpty()
}