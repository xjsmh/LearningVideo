这个项目用于Android视频编解码和OpenGL知识学习。

目前是呈现为一个转码APP：播放视频的过程中同时编码为一个新视频。


整个流程都是串行执行：

解码一帧->处理一帧->渲染一帧->编码一帧


模块划分：

Core：使用 Handler 实现类似状态机的工作方式，串联 解码模块-渲染模块-编码模块 的工作。

Decoder：解码模块，涉及 MediaCodec, MediaExtractor。

Encoder：编码模块，涉及 MediaCodec，MediaMuxer，EGL共享资源。

Renderer：渲染模块，用于渲染视频

    Renderer1：TextureView + SurfaceTexture + EGL 实现。
  
    Renderer2：GLSurfaceView + SurfaceTexture + EGL 实现。
  
    Renderer3：SurfaceTexture + EGL 实现。
  
    Renderer4：EGL + YUV转RGB 实现。
  
Filter：滤镜模块，用于处理解码后渲染前的视频帧，嵌入在Renderer中。

    DoNothingButUploaderImage：啥也没干，只负责上传纹理（强制作为 Renderer1，Renderer2，Renderer3 的第一个Filter）。
  
    NV12ToRGBA：实现NV12格式的YUV转RGBA（强制作为 Renderer4 的第一个Filter）。
  
    GrayMask：彩色图转灰度图，属于可选Filter，涉及FBO离屏渲染。
  
    FrameCapture：视频播放过程中将视频帧保存为本地图片，属于可选Filter，涉及像素读取。
  
  
















































  
  
