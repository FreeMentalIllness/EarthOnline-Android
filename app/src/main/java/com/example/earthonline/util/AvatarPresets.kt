package com.example.earthonline.util

/**
 * v1.0.6.6（死命令 #2）：默认 Emoji 头像预设已彻底移除。
 *
 * 更换头像现在只提供两条路径：
 *  - 从相册上传图片（[ImageStore] 持久化原图路径）；
 *  - 重置为默认头像（ProfileViewModel.resetAvatar 清空预设 key，
 *    由 ui/components/Avatar.kt 用极简 User 轮廓矢量图标 Icons.Rounded.PersonOutline 兜底）。
 *
 * 历史遗留的 AVATAR_EMOJI / AVATAR_LABEL / AVATAR_PRESET_OPTIONS / avatarEmoji
 * 全部删除，绝不再向用户暴露任何 Emoji 头像选项。
 */
