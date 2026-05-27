import json
import os
from datetime import datetime, timezone

import boto3
from botocore.exceptions import ClientError

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
    final_status = event.get("finalStatus")
    failure_error = event.get("failureError")
    failure_cause = event.get("failureCause")

    standard_document = read_json_if_exists(result_bucket_name, standard_result_object_key)
    llm_document = read_json_if_exists(result_bucket_name, llm_result_object_key)
    timestamp = datetime.now(timezone.utc).isoformat()
    status = resolve_status(final_status, standard_document, llm_document)

    merged_document = None
    if standard_document is not None or llm_document is not None:
        merged_document = build_merged_document(
            scan_id,
            account_id,
            status,
            timestamp,
            standard_document,
            llm_document,
            failure_error,
            failure_cause,
        )

    if merged_document is not None:
        s3.put_object(
            Bucket=result_bucket_name,
            Key=result_object_key,
            ContentType="application/json",
            Body=json.dumps(merged_document).encode("utf-8"),
        )

    update_scan_status(
        scan_id,
        status,
        timestamp,
        result_bucket_name if merged_document is not None else None,
        result_object_key if merged_document is not None else None,
        failure_error,
        failure_cause,
    )

    return {
        "scanId": scan_id,
        "status": status,
        "updatedAt": timestamp,
        "resultBucketName": result_bucket_name if merged_document is not None else None,
        "resultObjectKey": result_object_key if merged_document is not None else None,
    }


def resolve_status(explicit_status, standard_document, llm_document):
    if explicit_status:
        return explicit_status
    if standard_document is not None and llm_document is not None:
        return "COMPLETED"
    if standard_document is not None or llm_document is not None:
        return "FAILED"
    return "FAILED"


def build_merged_document(
    scan_id,
    account_id,
    status,
    timestamp,
    standard_document,
    llm_document,
    failure_error,
    failure_cause,
):
    document = {
        "scanId": scan_id,
        "accountId": account_id,
        "status": status,
        "updatedAt": timestamp,
        "engines": {},
        "findings": [],
    }
    if status == "COMPLETED":
        document["completedAt"] = timestamp
    else:
        document["failedAt"] = timestamp
    if standard_document is not None:
        document["engines"]["standard"] = standard_document
        document["findings"].extend(standard_document.get("findings", []))
    if llm_document is not None:
        document["engines"]["llm"] = llm_document
        document["findings"].extend(llm_document.get("findings", []))
    if failure_error or failure_cause:
        document["failure"] = {
            "error": failure_error,
            "cause": failure_cause,
        }
    return document


def update_scan_status(scan_id, status, timestamp, bucket_name, object_key, failure_error, failure_cause):
    names = {"#status": "status"}
    values = {
        ":status": {"S": status},
        ":timestamp": {"S": timestamp},
    }
    update_expression = "SET #status = :status, updatedAt = :timestamp"

    if status == "COMPLETED":
        update_expression += ", completedAt = :timestamp"
    else:
        update_expression += ", failedAt = :timestamp"

    if bucket_name and object_key:
        values[":bucket"] = {"S": bucket_name}
        values[":key"] = {"S": object_key}
        update_expression += ", resultBucketName = :bucket, resultObjectKey = :key"

    if failure_error:
        values[":failureError"] = {"S": failure_error}
        update_expression += ", failureError = :failureError"

    if failure_cause:
        values[":failureCause"] = {"S": failure_cause}
        update_expression += ", failureCause = :failureCause"

    dynamodb.update_item(
        TableName=SCAN_TABLE_NAME,
        Key={"scanId": {"S": scan_id}},
        UpdateExpression=update_expression,
        ExpressionAttributeNames=names,
        ExpressionAttributeValues=values,
    )


def read_json_if_exists(bucket_name, object_key):
    if not object_key:
        return None
    try:
        response = s3.get_object(Bucket=bucket_name, Key=object_key)
    except ClientError as error:
        if error.response.get("Error", {}).get("Code") in {"NoSuchKey", "404"}:
            return None
        raise
    return json.loads(response["Body"].read().decode("utf-8"))
