using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_btn
{
    [TestClass]
    public class Bai07
    {
        public TestContext TestContext { get; set; }

        [TestMethod]
        [DeploymentItem("data_csv\\Bai07.csv")]
        [DataSource("Microsoft.VisualStudio.TestTools.DataSource.CSV", "|DataDirectory|\\data_csv\\Bai07.csv", "Bai07#csv", DataAccessMethod.Sequential)]
        public void Sum_DataDriven()
        {
            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            string exp_k_str = TestContext.DataRow["expected_k"].ToString();

            if (exp_k_str.ToLower() == "exception")
            {
                bool exceptionThrown = false;

                try
                {
                    long n = Convert.ToInt64(TestContext.DataRow["n"]);

                    long s;
                    m.Sum(n, out s);
                }
                catch (Exception)
                {
                    exceptionThrown = true;
                }

                Assert.IsTrue(
                    exceptionThrown,
                    "Test case này mong đợi văng lỗi, nhưng hàm lại chạy bình thường."
                );
            }
            else
            {
                long n = Convert.ToInt64(TestContext.DataRow["n"]);
                long exp_k = Convert.ToInt64(exp_k_str);
                long exp_s = Convert.ToInt64(TestContext.DataRow["expected_s"]);

                long s;
                long act_k = m.Sum(n, out s);

                Assert.AreEqual(exp_k, act_k);
                Assert.AreEqual(exp_s, s);
            }
        }
    }
}
