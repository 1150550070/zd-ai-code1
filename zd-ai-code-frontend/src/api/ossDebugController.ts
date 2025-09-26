// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /oss/debug/config */
export async function getOssConfig(options?: { [key: string]: any }) {
  return request<API.BaseResponseMapStringObject>('/oss/debug/config', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /oss/debug/test-url */
export async function testUrlGeneration(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.testUrlGenerationParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringString>('/oss/debug/test-url', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
