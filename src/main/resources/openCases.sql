select t.bonitaInvoiceID, t.bonitaUserID, t.transactionID, t.registrationNumber, t.transactionDateTime, t.grossAmount, t.netAmount, t.postedDateTime, t.costCentre, t.transactionReason,t.fuelType,t.transactionExplanation,t.claimStatus,t.site
from Transactions t
where t.claimStatus <> 'Completed'
and t.site = :s
and ( t.transactionID like :q
or t.registrationNumber like :q
or t.costCentre like :q
or t.claimStatus like :q
)