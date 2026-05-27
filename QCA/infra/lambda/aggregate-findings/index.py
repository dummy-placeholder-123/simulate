import json
import os
from datetime import datetime, timezone

import boto3

s3 = boto3.client("s3")
dynamodb = boto3.client("dynamodb")
SCAN_TABLE_NAME = os.environ["SCAN_TABLE_NAME"]


def handler(event, _context):
    scan_id = event["scanId"]
    account_id = event["accountId"]
    result_bucket_name = event["resultBucketName"]
    result_object_key = event["resultObjectKey"]
    standard_result_object_key = event["standardResultObjectKey"]
    llm_result_object_key = event["llmResultObjectKey"]

    standard_document = read_json(result_bucket_name, standard_result_object_key)
    llm_document = read_json(result_bucket_name, llm_result_object_key)
    completed_at = datetime.now(timezone.utc).isoformat()

    merged_document = {
        "scanId": scan_id,
        "accountId": account_id,
        "status": "COMPLETED",
        "completedAt": completed_at,
        "engines": {
            "standard": standard_document,
            "llm": llm_document,
        },
        "findings": [
            *standard_document.get("findings", []),
            *llm_document.get("findings", []),
        ],
    }

    s3.put_object(
        Bucket=result_bucket_name,
        Key=result_object_key,
        ContentType="application/json",
        Body=json.dumps(merged_document).encode("utf-8"),
    )

    dynamodb.update_item(
        TableName=SCAN_TABLE_NAME,
        Key={"scanId": {"S": scan_id}},
        UpdateExpression="SET #status = :status, completedAt = :completedAt, updatedAt = :completedAt, resultBucketName = :bucket, resultObjectKey = :key",
        ExpressionAttributeNames={"#status": "status"},
        ExpressionAttributeValues={
            ":status": {"S": "COMPLETED"},
            ":completedAt": {"S": completed_at},
            ":bucket": {"S": result_bucket_name},
            ":key": {"S": result_object_key},
        },
    )

    return {
        "scanId": scan_id,
        "status": "COMPLETED",
        "completedAt": completed_at,
        "resultBucketName": result_bucket_name,
        "resultObjectKey": result_object_key,
    }


def read_json(bucket_name, object_key):
    response = s3.get_object(Bucket=bucket_name, Key=object_key)
    return json.loads(response["Body"].read().decode("utf-8"))
