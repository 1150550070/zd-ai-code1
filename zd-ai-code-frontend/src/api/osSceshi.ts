// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 测试截图上传 测试网页截图生成并上传到OSS POST /oss/test/screenshot */
export async function testScreenshot(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.testScreenshotParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseObject>('/oss/test/screenshot', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
