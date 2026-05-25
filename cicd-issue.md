# CI/CD Incident SOP (CDK + CloudFormation + ECS/ECR)

## Purpose
This SOP defines how to diagnose and fix production CI/CD failures caused by CloudFormation drift, logical ID changes, and ECS/ECR dependency ordering issues.

## Scope
Use this SOP for incidents like:
- `Unable to retrieve Arn attribute for AWS::ECR::Repository`
- `already exists in stack`
- ECS task definition failures during `cdk deploy`
- CloudFormation stack stuck in `UPDATE_ROLLBACK_COMPLETE`

## Severity and Immediate Actions
1. Stop repeated deploy retries on the same broken commit.
2. Freeze manual AWS console edits until incident is closed.
3. Capture the failed workflow run URL and stack events.
4. Confirm blast radius:
- Is this infra-only?
- Are running services impacted?
- Is data plane affected?

## Fast Triage Checklist
1. Confirm deployed commit SHA in the failed workflow run.
2. Confirm current stack state:
`aws cloudformation describe-stacks --stack-name InfraStack`
3. Pull newest stack events:
`aws cloudformation describe-stack-events --stack-name InfraStack --max-items 50`
4. Verify local synth from the same commit:
`cd QCA/infra && cdk synth`
5. Check synthesized template for risky references:
- ECS `TaskDefinition` must not depend on ECR repo Arn in bootstrap mode.
- Bootstrap image should be public placeholder if first deploy ordering is required.

## Known Issue: ECR Repo Deleted Manually, Stack State Stale
Symptoms:
- CloudFormation shows ECR resource `CREATE_COMPLETE`.
- Physical ECR repo does not exist.
- Deploy fails with ECR Arn / already exists errors.

Recovery Playbook (Production Safe):
1. Deploy a repair commit that removes only the ECR resource from CDK template.
2. Wait for stack update to complete and old ECR logical resource to leave stack state.
3. Deploy second commit restoring ECR resource with desired logical ID and config.
4. Run infra deploy again to recreate repo cleanly.
5. Run engine deploy to push image and update ECS service.

Notes:
- `DELETE_SKIPPED` on ECR with `RETAIN` is expected.
- Do not remove unrelated resources in repair deploy.

## Known Issue: Logical ID Rename Collision
Symptoms:
- New ECR logical resource create fails: `qca-engine already exists in stack`.

Fix:
1. Use the two-step repair above.
2. Avoid immediate rename+create when old logical resource still exists in stack state.

## Stateful Resource Incidents
Stateful resources must be handled differently from recreatable resources. Do not use logical ID rename as a quick repair for data-bearing resources.

Stateful resources in this project:
- DynamoDB tables: `qca-scans`, `qca-scan-idempotency`
- S3 bucket: `qca-scan-uploads-564061926474-us-east-1`
- SQS queues with unprocessed messages: `qca-scan-queue`, `qca-scan-dlq`

Mostly recreatable resources:
- ECR repository, if images can be rebuilt from source
- ECS service/task definition, if no local task state matters
- CloudWatch log group, if logs are not required for audit retention

## Stateful Resource Recovery Rules
1. Do not rename logical IDs for DynamoDB or S3 unless a migration plan exists.
2. Do not remove stateful resources from the template just to clear stack state.
3. Confirm whether the physical resource still exists before any repair.
4. Take a backup or export before repair.
5. Prefer import/adopt over recreate for production data.
6. Get explicit approval before deleting, replacing, or orphaning stateful resources.

## DynamoDB Recovery
Use this when a DynamoDB table is manually deleted, drifted, or stuck in CloudFormation state.

If the physical table still exists:
1. Run CloudFormation drift detection.
2. Fix CDK to match the real table settings.
3. Use CloudFormation resource import if the table exists outside stack management.
4. Do not create a replacement table with the same name without confirming no data loss.

If the physical table was deleted:
1. Confirm backups, PITR, or exports are available.
2. Restore table from backup or point-in-time recovery.
3. Import restored table into CloudFormation, or update CDK to manage the restored physical name.
4. Verify item counts, GSIs, TTL config, and IAM permissions.

Required checks:
- PITR enabled status
- TTL attribute
- GSI definitions
- Billing mode
- Table encryption
- Removal policy

## S3 Recovery
Use this when an S3 bucket is manually deleted, drifted, or removed from stack state.

If the physical bucket still exists:
1. Do not delete/recreate it.
2. Fix bucket policy, CORS, encryption, versioning, lifecycle, and public access settings.
3. Import the bucket into CloudFormation if it is no longer stack-managed.

If the physical bucket was deleted:
1. Confirm whether versioning, replication, or backups can restore objects.
2. Recreate bucket only after confirming object-loss impact.
3. Restore required objects.
4. Verify bucket policy, public access block, encryption, CORS, and presigned-upload flow.

Required checks:
- Object count and critical prefixes
- Versioning state
- Public access block
- Bucket policy
- CORS config
- Encryption
- Lifecycle rules

## SQS Recovery
SQS is stateful when messages are in flight or waiting. Treat queue replacement as data loss unless queue depth is confirmed empty.

Before queue repair:
1. Check visible and in-flight message counts.
2. Check DLQ depth.
3. Pause producers if needed.
4. Drain or redrive messages before destructive changes.

Required checks:
- `ApproximateNumberOfMessagesVisible`
- `ApproximateNumberOfMessagesNotVisible`
- DLQ message count
- Visibility timeout
- Retention period
- Redrive policy

## Resource Import Preferred Path
Use CloudFormation import when a real production resource exists but stack state is missing or wrong.

General flow:
1. Update CDK template to define the resource exactly as it exists.
2. Run `cdk synth`.
3. Start CloudFormation resource import.
4. Provide the physical identifier, such as table name or bucket name.
5. Complete import and run drift detection.

Use import for:
- Existing DynamoDB tables
- Existing S3 buckets
- Existing queues with important messages

Avoid import for:
- Short-lived ECS task definitions
- ECR repos that are empty and rebuildable
- Log groups with no retention requirement

## Logical ID Rename Policy
Logical ID rename is allowed only for resources that are safe to replace or already physically gone.

Allowed with caution:
- ECR repository when images can be rebuilt
- ECS task definition
- ECS service when downtime is accepted or service has no state
- CloudWatch log group when logs are non-critical

Not allowed without migration plan:
- DynamoDB table
- S3 bucket
- SQS queue with messages
- KMS key
- IAM role used by external systems
- Route53 records for production traffic

## Safe Deployment Sequence (This Project)
1. Infra deploy:
- Creates DynamoDB/SQS/StepFunctions/VPC/ECS service shell.
- ECS desired count remains `0`.
- Task definition uses public placeholder image.
2. Engine deploy:
- Builds and pushes image to `qca-engine`.
- Updates ECS task definition image.
- Sets desired count to `1` and enables scaling bounds.

## Change Safety Rules
1. Pin CDK and constructs versions in `QCA/infra/pom.xml`.
2. Do not use version ranges in production CI.
3. Never manually delete CloudFormation-managed resources in prod.
4. If emergency manual delete happens, open incident and run this SOP.
5. Treat logical ID changes as migration events, not routine edits.
6. Set `RemovalPolicy.RETAIN` for production data stores.
7. Enable backups/PITR for production DynamoDB tables.
8. Enable versioning for production S3 buckets when object recovery matters.

## Preventive Controls
1. IAM guardrails:
- Restrict direct delete permissions for managed ECR/ECS/CFN resources.
- Require break-glass approval for DynamoDB, S3, KMS, and SQS deletes.
2. Drift checks:
- Run scheduled `detect-stack-drift` for prod stacks.
3. CI checks:
- Enforce synth and template diff review before deploy.
4. Runbook readiness:
- Keep this SOP in repo and link it in workflow failure notifications.
5. Backups:
- Monitor backup/PITR status for DynamoDB.
- Monitor S3 versioning and lifecycle policy changes.
- Alarm on DLQ depth and queue age.

## Post-Incident Closure
1. Record root cause.
2. Record exact repair commits and deploy timestamps.
3. Confirm stack status `UPDATE_COMPLETE`.
4. Confirm ECS service healthy and worker consuming queue.
5. Add preventive action item with owner and due date.

## Incident Template
Use this format in ticket/Slack:
- Incident ID:
- Start time (UTC):
- Impact:
- Failed stack/resource:
- Error excerpt:
- Root cause:
- Repair steps taken:
- Recovery time:
- Preventive actions:
